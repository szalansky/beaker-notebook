/*
 *  Copyright 2014 TWO SIGMA OPEN SOURCE, LLC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.twosigma.beaker.core.rest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sun.jersey.api.Responses;
import com.twosigma.beaker.core.module.config.BeakerConfig;
import com.twosigma.beaker.shared.module.util.GeneralUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.jvnet.winp.WinProcess;

/**
 * This is the service that locates a plugin service. And a service will be started if the target
 * service doesn't exist. See {@link locatePluginService} for details
 */
@Path("plugin-services")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class PluginServiceLocatorRest {
  // these 3 times are in millis
  private static final int RESTART_ENSURE_RETRY_MAX_WAIT = 30*1000;
  private static final int RESTART_ENSURE_RETRY_INTERVAL = 10;
  private static final int RESTART_ENSURE_RETRY_MAX_INTERVAL = 2500;

  private final String nginxDir;
  private final String nginxBinDir;
  private final String nginxStaticDir;
  private final String nginxServDir;
  private final String nginxExtraRules;
  private final String pluginDir;
  private final String nginxCommand;
  private final Integer portBase;
  private final Integer servPort;
  private final Integer corePort;
  private final Integer reservedPortCount;
  private final Map<String, String> pluginLocations;
  private final Map<String, List<String>> pluginArgs;
  private final Map<String, String[]> pluginEnvps;
  private final OutputLogService outputLogService;

  private final String nginxTemplate;
  private final Map<String, PluginConfig> plugins = new HashMap<>();
  private Process nginxProc;
  private int portSearchStart;

  @Inject
  private PluginServiceLocatorRest(
      BeakerConfig bkConfig,
      OutputLogService outputLogService,
      GeneralUtils utils) throws IOException {
    this.nginxDir = bkConfig.getNginxDirectory();
    this.nginxBinDir = bkConfig.getNginxBinDirectory();
    this.nginxStaticDir = bkConfig.getNginxStaticDirectory();
    this.nginxServDir = bkConfig.getNginxServDirectory();
    this.nginxExtraRules = bkConfig.getNginxExtraRules();
    this.pluginDir = bkConfig.getPluginDirectory();
    this.portBase = bkConfig.getPortBase();
    this.servPort = this.portBase + 1;
    this.corePort = this.portBase + 2;
    this.reservedPortCount = bkConfig.getReservedPortCount();
    this.pluginLocations = bkConfig.getPluginLocations();
    this.pluginEnvps = bkConfig.getPluginEnvps();
    this.pluginArgs = new HashMap<>();
    this.outputLogService = outputLogService;
    this.nginxTemplate = utils.readFile(this.nginxDir + "/nginx.conf.template");
    if (nginxTemplate == null) {
      throw new RuntimeException("Cannot get nginx template");
    }
    String cmd = this.nginxBinDir + (this.nginxBinDir.isEmpty() ? "nginx" : "/nginx");
    if (windows()) {
      cmd += (" -p \"" + this.nginxServDir + "\"");
      cmd += (" -c \"" + this.nginxServDir + "/conf/nginx.conf\"");
    } else {
      cmd += (" -p " + this.nginxServDir);
      cmd += (" -c " + this.nginxServDir + "/conf/nginx.conf");
    }
    this.nginxCommand = cmd;

    // record plugin options from cli and to pass through to individual plugins
    for (Map.Entry<String, String> e: bkConfig.getPluginOptions().entrySet()) {
      addPluginArgs(e.getKey(), e.getValue());
    }

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("\nshutting down beaker");
        shutdown();
        System.out.println("done, exiting");
      }
    });

    portSearchStart = this.portBase + this.reservedPortCount;
  }

  private boolean windows() {
    return System.getProperty("os.name").contains("Windows");
  }

  private static boolean windowsStatic() {
    return System.getProperty("os.name").contains("Windows");
  }

  public void start() throws InterruptedException, IOException {
    startReverseProxy();
  }

  private void startReverseProxy() throws InterruptedException, IOException {
    generateNginxConfig();
    System.out.println("running nginx: " + this.nginxCommand);
    Process proc = Runtime.getRuntime().exec(this.nginxCommand);
    startGobblers(proc, "nginx", null, null);
    this.nginxProc = proc;
  }

  private void shutdown() {
    StreamGobbler.shuttingDown();

    if (windows()) {
      new WinProcess(this.nginxProc).killRecursively();
    } else {
      this.nginxProc.destroy(); // send SIGTERM
    }
    for (PluginConfig p : this.plugins.values()) {
      p.shutDown();
    }
  }

  /**
   * locatePluginService
   * locate the service that matches the passed-in information about a service and return the
   * base URL the client can use to connect to the target plugin service. If such service
   * doesn't exist, this implementation will also start the service.
   *
   * @param pluginId
   * @param command name of the starting script
   * @param nginxRules rules to help setup nginx proxying
   * @param startedIndicator string indicating that the plugin has started
   * @param startedIndicatorStream stream to search for indicator, null defaults to stdout
   * @param recordOutput boolean, record out/err streams to output log service or not, null defaults
   * to false
   * @param waitfor if record output log service is used, string to wait for before logging starts
   * @return the base url of the service
   * @throws InterruptedException
   * @throws IOException
   */
  @GET
  @Path("/{plugin-id}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response locatePluginService(
      @PathParam("plugin-id") String pluginId,
      @QueryParam("command") String command,
      @QueryParam("nginxRules") @DefaultValue("location %(base_url)s/ {proxy_pass http://127.0.0.1:%(port)s/;}") String nginxRules,
      @QueryParam("startedIndicator") String startedIndicator,
      @QueryParam("startedIndicatorStream") @DefaultValue("stdout") String startedIndicatorStream,
      @QueryParam("recordOutput") @DefaultValue("false") boolean recordOutput,
      @QueryParam("waitfor") String waitfor)
      throws InterruptedException, IOException {

    PluginConfig pConfig = this.plugins.get(pluginId);
    if (pConfig != null && pConfig.isStarted()) {
      System.out.println("plugin service " + pluginId +
          " already started at" + pConfig.getBaseUrl());
      return buildResponse(pConfig.getBaseUrl(), false);
    }

    synchronized (this) {
      final int port = getNextAvailablePort(this.portSearchStart);
      final String baseUrl = "/" + generatePrefixedRandomString(pluginId, 12).replaceAll("[\\s]", "");
      pConfig = new PluginConfig(port, nginxRules, baseUrl);
      this.portSearchStart = pConfig.port + 1;
      this.plugins.put(pluginId, pConfig);

      // restart nginx to reload new config
      String restartId = generateNginxConfig();
      String restartPath = "\"" + this.nginxServDir + "/restart_nginx\"";
      String restartCommand = this.nginxCommand + " -s reload";
      System.err.println("restartCommand=" + restartCommand);
      Process restartproc = Runtime.getRuntime().exec(restartCommand);
      startGobblers(restartproc, "restart-nginx-" + pluginId, null, null);
      restartproc.waitFor();

      // spin until restart is done
      String url = "http://127.0.0.1:" + servPort + "/restart." + restartId + "/present.html";
      try {
        spinCheck(url);
      } catch (Throwable t) {
        System.err.println("Nginx restart time out plugin =" + pluginId);
        throw new NginxRestartFailedException("nginx restart failed.\n"
            + "url=" + url + "\n"
            + "message=" + t.getMessage());
      }
    }

    String fullCommand = command;
    String baseCommand;
    String args;
    int space = command.indexOf(' ');
    if (space > 0) {
      baseCommand = command.substring(0, space);
      args = command.substring(space); // include space
    } else {
      baseCommand = command;
      args = " ";
    }
    if (Files.notExists(Paths.get(baseCommand))) {
      if (this.pluginLocations.containsKey(pluginId)) {
        fullCommand = this.pluginLocations.get(pluginId) + "/" + baseCommand;
      }
      if (Files.notExists(Paths.get(fullCommand))) {
        fullCommand = this.pluginDir + "/" + baseCommand;
        if (Files.notExists(Paths.get(fullCommand))) {
          throw new PluginServiceNotFoundException(
              "plugin service: " + pluginId + " not found"
              + " and fail to start it with: " + command);
        }
      }
    }

    if (windows()) {
      fullCommand = "\"" + fullCommand + "\"";
    } else {
      fullCommand += args; // XXX should be in windows too?
    }

    List<String> extraArgs = this.pluginArgs.get(pluginId);
    if (extraArgs != null) {
      fullCommand += StringUtils.join(extraArgs, " ");
    }
    fullCommand += " " + Integer.toString(pConfig.port);
    fullCommand += " " + Integer.toString(corePort);

    String[] env = this.pluginEnvps.get(pluginId);
    if (windows()) {
      fullCommand = "python " + fullCommand;
    }
    System.out.println("Running: " + fullCommand);
    Process proc = Runtime.getRuntime().exec(fullCommand, env);

    if (startedIndicator != null && !startedIndicator.isEmpty()) {
      InputStream is = startedIndicatorStream.equals("stderr") ?
          proc.getErrorStream() : proc.getInputStream();
      InputStreamReader ir = new InputStreamReader(is);
      BufferedReader br = new BufferedReader(ir);
      String line = "";
      while ((line = br.readLine()) != null) {
        System.out.println("looking on " + startedIndicatorStream + " found:" + line);
        if (line.indexOf(startedIndicator) >= 0) {
          System.out.println("Acknowledge " + pluginId + " plugin started");
          break;
        }
      }
    }
    startGobblers(proc, pluginId, recordOutput ? this.outputLogService : null, waitfor);

    pConfig.setProcess(proc);
    System.out.println("Done starting " + pluginId);
    return buildResponse(pConfig.getBaseUrl(), true);
  }

  @GET
  @Path("getAvailablePort")
  public int getAvailablePort() {
    int port;
    synchronized (this) {
      port = getNextAvailablePort(this.portSearchStart++);
    }
    return port;
  }

  private static Response buildResponse(String baseUrl, boolean created) {
    baseUrl = ".." + baseUrl;
    return Response
        .status(created ? Response.Status.CREATED : Response.Status.OK)
        .entity(baseUrl)
        .location(URI.create(baseUrl))
        .build();
  }

  private static boolean spinCheck(String url)
      throws IOException, InterruptedException
  {

    int interval = RESTART_ENSURE_RETRY_INTERVAL;
    int totalTime = 0;

    while (totalTime < RESTART_ENSURE_RETRY_MAX_WAIT) {
      if (Request.Get(url)
          .execute()
          .returnResponse()
          .getStatusLine()
          .getStatusCode() == HttpStatus.SC_OK) {
        return true;
      }
      Thread.sleep(interval);
      totalTime += interval;
      interval *= 1.5;
      if (interval > RESTART_ENSURE_RETRY_MAX_INTERVAL)
        interval = RESTART_ENSURE_RETRY_MAX_INTERVAL;
    }
    throw new RuntimeException("Spin check timed out");
  }

  private static class PluginServiceNotFoundException extends WebApplicationException {
    public PluginServiceNotFoundException(String message) {
      super(Response.status(Responses.NOT_FOUND)
          .entity(message).type("text/plain").build());
    }
  }

   private static class NginxRestartFailedException extends WebApplicationException {
    public NginxRestartFailedException(String message) {
      super(Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
          .entity(message).type("text/plain").build());
    }
  }

  private void addPluginArgs(String plugin, String arg) {
    List<String> args = this.pluginArgs.get(plugin);
    if (args == null) {
      args = new ArrayList<>();
      this.pluginArgs.put(plugin, args);
    }
    args.add(arg);
  }

  private String generateNginxConfig() throws IOException, InterruptedException {

    java.nio.file.Path confDir = Paths.get(this.nginxServDir, "conf");
    java.nio.file.Path logDir = Paths.get(this.nginxServDir, "logs");
    java.nio.file.Path nginxClientTempDir = Paths.get(this.nginxServDir, "client_temp");
    java.nio.file.Path htmlDir = Paths.get(this.nginxServDir, "html");

    if (Files.notExists(confDir)) {
      confDir.toFile().mkdirs();
    }
    if (Files.notExists(logDir)) {
      logDir.toFile().mkdirs();
    }
    if (Files.notExists(nginxClientTempDir)) {
      nginxClientTempDir.toFile().mkdirs();
    }

    if (Files.notExists(htmlDir)) {
      Files.createDirectory(htmlDir);
      Files.copy(Paths.get(this.nginxStaticDir + "/50x.html"),
                 Paths.get(htmlDir.toString() + "/50x.html"));
      Files.copy(Paths.get(this.nginxStaticDir + "/present.html"),
                 Paths.get(htmlDir.toString() + "/present.html"));
      //Files.copy(Paths.get(this.nginxStaticDir + "/favicon.ico"),
      //           Paths.get(htmlDir.toString() + "/favicon.ico"));
    }

    String restartId = RandomStringUtils.random(12, false, true);
    String ngixConfig = this.nginxTemplate;
    StringBuilder pluginSection = new StringBuilder();
    for (PluginConfig pConfig : this.plugins.values()) {
      String nginxRule = pConfig.getNginxRules()
          .replace("%(port)s", Integer.toString(pConfig.getPort()))
          .replace("%(base_url)s", pConfig.getBaseUrl());
      pluginSection.append(nginxRule + "\n\n");
    }
    ngixConfig = ngixConfig.replace("%(plugin_section)s", pluginSection.toString());
    ngixConfig = ngixConfig.replace("%(extra_rules)s", this.nginxExtraRules);
    ngixConfig = ngixConfig.replace("%(host)s", InetAddress.getLocalHost().getHostName());
    ngixConfig = ngixConfig.replace("%(port_main)s", Integer.toString(this.portBase));
    ngixConfig = ngixConfig.replace("%(port_beaker)s", Integer.toString(this.corePort));
    ngixConfig = ngixConfig.replace("%(port_clear)s", Integer.toString(this.servPort));
    if (windows()) {
      String tempDir = nginxClientTempDir.toFile().getPath();
      // Nginx interprets strings in unix style so backslash confuses it.
      ngixConfig = ngixConfig.replace("%(client_temp_dir)s", tempDir.replace("\\", "/"));
    } else {
      ngixConfig = ngixConfig.replace("%(client_temp_dir)s", nginxClientTempDir.toFile().getPath());
    }
    ngixConfig = ngixConfig.replace("%(restart_id)s", restartId);

    // write template to file
    java.nio.file.Path targetFile = Paths.get(this.nginxServDir, "conf/nginx.conf");
    if (Files.exists(targetFile)) {
      Files.delete(targetFile);
    }
    try (PrintWriter out = new PrintWriter(targetFile.toFile())) {
      out.print(ngixConfig);
    }

    return restartId;
  }

  private static int getNextAvailablePort(int start) {
    final int SEARCH_LIMIT = 100;
    for (int p = start; p < start + SEARCH_LIMIT; ++p) {
      if (isPortAvailable(p)) {
        return p;
      }
    }

    throw new RuntimeException("out of ports error");
  }

  private static String generatePrefixedRandomString(String prefix, int randomPartLength) {
    // Use lower case due to nginx bug handling mixed case locations
    // (fixed in 1.5.6 but why depend on it).
    return prefix.toLowerCase() + "." + RandomStringUtils.random(randomPartLength, false, true);
  }

  @GET
  @Path("getIPythonVersion")
  @Produces(MediaType.APPLICATION_JSON)
  public String getIPythonVersion()
      throws IOException
  {
    Process proc;
    if (windows()) {
      String cmd = "python " + "\"" + this.pluginDir + "/ipythonPlugins/ipython/ipythonVersion\"";
      proc = Runtime.getRuntime().exec(cmd);
    } else {
      proc = Runtime.getRuntime().exec("ipython --version");
    }
    BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    String line = br.readLine();
    return line;
  }

  private static class PluginConfig {

    private final int port;
    private final String nginxRules;
    private Process proc;
    private final String baseUrl;

    PluginConfig(int port, String nginxRules, String baseUrl) {
      this.port = port;
      this.nginxRules = nginxRules;
      this.baseUrl = baseUrl;
    }

    int getPort() {
      return this.port;
    }

    String getBaseUrl() {
      return this.baseUrl;
    }

    String getNginxRules() {
      return this.nginxRules;
    }

    void setProcess(Process proc) {
      this.proc = proc;
    }

    boolean isStarted () {
      return this.proc != null;
    }

    void shutDown() {
      if (this.isStarted()) {
        if (windowsStatic()) {
          new WinProcess(this.proc).killRecursively();
        } else {
          this.proc.destroy(); // send SIGTERM
        }
      }
    }

  }

  private static boolean isPortAvailable(int port) {

    ServerSocket ss = null;
    DatagramSocket ds = null;
    try {
      ss = new ServerSocket(port);
      ss.setReuseAddress(true);
      ds = new DatagramSocket(port);
      ds.setReuseAddress(true);
      return true;
    } catch (IOException e) {
    } finally {
      if (ds != null) {
        ds.close();
      }
      if (ss != null) {
        try {
          ss.close();
        } catch (IOException e) {
          /* should not be thrown */
        }
      }
    }
    return false;
  }

  private static void startGobblers(
      Process proc,
      String name,
      OutputLogService outputLogService,
      String waitfor) {
    StreamGobbler errorGobbler =
        new StreamGobbler(proc.getErrorStream(), "stderr", name,
        outputLogService, waitfor);
    errorGobbler.start();

    StreamGobbler outputGobbler =
        new StreamGobbler(proc.getInputStream(), "stdout", name,
        outputLogService);
    outputGobbler.start();
  }
}
