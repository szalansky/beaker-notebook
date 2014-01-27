/**
 * Created by alee on 1/26/14.
 */
/*
 *  Copyright 2014 TWO SIGMA INVESTMENTS, LLC
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
/**
 * File menu plugin
 * This adds file system specific menu items to the File menu.
 */
(function () {
    'use strict';

    var load = function () {
        var deferred = bkHelper.newDeferred();
        Dropbox.choose({
            success: function (files) {
                $.getJSON(files[0].link)
                    .done(function (json) {
                        deferred.resolve({
                            link: files[0].link,
                            json: json
                        });
                    })
                    .fail(function ( jqxhr, textStatus, error) {
                        var err = textStatus + ", " + error;
                        deferred.reject(err);
                    })
            },
            linkType: "direct"
        });
        return deferred.promise;
    };

    var errorHandler = function (data, status, headers, config) {
        bkHelper.showErrorModal(data);
        bkHelper.refreshRootScope();
    };

    var toAdd = [
        {
            parent: "File",
            submenu: "Open",
            items: [
                {
                    name: "Open... (Dropbox)",
                    tooltip: "Open a file from dropbox.com",
                    action: function () {
                        load().then(function (ret) {
                            console.log(ret);
                            var notebookJson = ret.json;
                            bkHelper.loadNotebook(notebookJson, true, "dropbox:" + ret.link);
                            bkHelper.evaluate("initialization");
                            document.title = ret.link.replace(/^.*[\\\/]/, '');
                        }, errorHandler);
                    }
                }
            ]
        }
    ];
    pluginObj.onReady(toAdd);
})();
