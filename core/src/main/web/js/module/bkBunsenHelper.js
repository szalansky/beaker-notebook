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
 * M_bkBunsenHelper
 * The docs go here ...
 *
 */
(function() {
  'use strict';
  var module = angular.module('M_bkBunsenHelper', [
    'M_bkCore',
    'M_bkShare',
    'M_bkHelper'
  ]);
  /**
   * bkBunsenHelper
   *
   */
  module.factory('bkBunsenHelper', function(bkBaseSessionModel, bkCoreManager, bkShare, $http, bkHelper, $dialog, $routeParams) {

    var generateSaveUrl = function(notebookName){
      var userId = $routeParams.userId;
      var projectId = $routeParams.projectId;
      return 'http://localhost:3000/api/users/' + userId + '/projects/' + projectId + '/notebooks/' + notebookName;
    };

    var generateNewFileUrl = function(){
      var userId = $routeParams.userId;
      var projectId = $routeParams.projectId;
      return 'http://localhost:3000/api/users/' + userId + '/projects/' + projectId + '/notebooks';
    };

    var bunsenSave = function(operation, url, notebook) {
      $http({method: operation, url: url, data: notebook }).
        success(function(data, status, headers, config) {
          bkBaseSessionModel.setEdited(false);
          if (operation === 'POST') {
            window.top.location.href = 'http://localhost:1111/#/projects/1/notebooks/' + notebook.name;
          }
        }).
        error(function(data, status, headers, config) {
          console.log('Failure Saving Notebook');
          console.log(data);
          window.alert('Error Saving Notebook');
        });
    };

    var bkBunsenHelper = {
      forDebugOnly: {
        bkBaseSessionModel: bkBaseSessionModel,
        bkCoreManager: bkCoreManager
      },

      saveNotebook: function() {
        var urlString = generateSaveUrl($routeParams.notebookName);
        var serializedNotebook = {
          data: bkHelper.getSessionData().content
        };
        bunsenSave('PUT', urlString, serializedNotebook);
      },

      saveNotebookAs: function(callback, template) {
        template = 'template/saveBunsenFile.html';

        var options = {
          backdrop: true,
          keyboard: true,
          backdropClick: true,
          controller: 'fileChooserController'
        };

        if (template.indexOf('template/') === 0) {
          options.templateUrl = template;
        } else {
          options.template = template;
        }

        $dialog.dialog(options)
          .open()
          .then(function(newName) {
            var urlString =  generateNewFileUrl();
            var serializedNotebook = {
              data: bkHelper.getSessionData().content
            };

            serializedNotebook.name = newName;
            bunsenSave('POST', urlString, serializedNotebook);
          });
      }

    };
    window.bkBunsenHelper = bkBunsenHelper; // TODO, we want to revisit the decision of making this global
    return bkBunsenHelper;
  });
})();
