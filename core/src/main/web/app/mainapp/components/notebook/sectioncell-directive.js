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

(function() {
  'use strict';
  var module = angular.module('bk.notebook');

  module.directive('bkSectionCell', function(
      bkUtils,
      bkEvaluatorManager,
      bkSessionManager,
      bkCoreManager,
      bkCellMenuPluginManager) {
    return {
      restrict: 'E',
      templateUrl: "./app/mainapp/components/notebook/sectioncell.html",
      //scope: { cell: "=" },
      controller: function($scope) {
        var notebookCellOp = bkSessionManager.getNotebookCellOp();
        $scope.toggleShowChildren = function() {
          if ($scope.cellmodel.collapsed === undefined) {
            $scope.cellmodel.collapsed = false;
          }
          $scope.cellmodel.collapsed = !$scope.cellmodel.collapsed;
        };
        $scope.isShowChildren = function() {
          if ($scope.cellmodel.collapsed === undefined) {
            $scope.cellmodel.collapsed = false;
          }
          return !$scope.cellmodel.collapsed;
        };
        $scope.getChildren = function() {
          return notebookCellOp.getChildren($scope.cellmodel.id);
        };
        $scope.resetTitle = function(newTitle) {
          $scope.cellmodel.title = newTitle;
          bkUtils.refreshRootScope();
        };
        $scope.$watch('cellmodel.title', function(newVal, oldVal) {
          if (newVal !== oldVal) {
            bkSessionManager.setNotebookModelEdited(true);
          }
        });
        $scope.$watch('cellmodel.initialization', function(newVal, oldVal) {
          if (newVal !== oldVal) {
            bkSessionManager.setNotebookModelEdited(true);
          }
        });
        $scope.cellview.menu.addItemToHead({
          name: "Delete section and all sub-sections",
          action: function() {
            notebookCellOp.deleteSection($scope.cellmodel.id);
          }
        });
        $scope.cellview.menu.addItem({
          name: "Change Header Level",
          items: [
            {
              name: "H1",
              action: function() {
                $scope.cellmodel.level = 1;
                notebookCellOp.reset();
              }
            },
            {
              name: "H2",
              action: function() {
                $scope.cellmodel.level = 2;
                notebookCellOp.reset();
              }
            },
            {
              name: "H3",
              action: function() {
                $scope.cellmodel.level = 3;
                notebookCellOp.reset();
              }
            },
            {
              name: "H4",
              action: function() {
                $scope.cellmodel.level = 4;
                notebookCellOp.reset();
              }
            }
          ]
        });
        $scope.isContentEditable = function() {
          return !bkSessionManager.isNotebookLocked();
        };

        $scope.getShareData = function() {
          return {
            cellModel: $scope.cellmodel,
            evViewModel: bkEvaluatorManager.getViewModel(),
            notebookModel: {
              cells: [$scope.cellmodel]
                  .concat(notebookCellOp.getAllDescendants($scope.cellmodel.id))
            }
          };
        };

        $scope.getShareMenuPlugin = function() {
          // the following cellType needs to match
          //plugin.cellType = "sectionCell"; in dynamically loaded cellmenu/sectionCell.js
          var cellType = "sectionCell";
          return bkCellMenuPluginManager.getPlugin(cellType);
        };
        $scope.cellview.menu.addItem({
          name: "Run all",
          action: function() {
            bkCoreManager.getBkApp().evaluate($scope.cellmodel.id).
                catch(function(data) {
                  console.error(data);
                });
          }
        });
        var shareMenu = {
          name: "Share",
          items: []
        };
        $scope.cellview.menu.addItem(shareMenu);
        $scope.$watch("getShareMenuPlugin()", function(getShareMenu) {
          if (getShareMenu) {
            shareMenu.items = getShareMenu($scope);
          }
        });
        $scope.isInitializationCell = function() {
          return $scope.cellmodel.initialization;
        };
        $scope.cellview.menu.addItem({
          name: "Initialization Cell",
          isChecked: function() {
            return $scope.isInitializationCell();
          },
          action: function() {
            if ($scope.isInitializationCell()) {
              $scope.cellmodel.initialization = undefined;
            } else {
              $scope.cellmodel.initialization = true;
            }
            notebookCellOp.reset();
          }
        });
        $scope.newCellMenuConfig = {
          isShow: function() {
            if (bkSessionManager.isNotebookLocked()) {
              return false;
            }
            return !$scope.cellmodel.hideTitle;
          },
          attachCell: function(newCell) {
            notebookCellOp.insertAfter($scope.cellmodel.id, newCell);
          }
        };
      },
      link: function(scope, element, attrs) {
        var titleElement = $(element.find(".bk-section-title").first());
        titleElement.bind('blur', function() {
          scope.resetTitle(titleElement.html().trim());
        });
        scope.$watch('isContentEditable()', function(newValue) {
          titleElement.attr('contenteditable', newValue);
        });
        if (scope.isInitializationCell()) {
          element.closest(".bkcell").addClass("initcell");
        } else {
          element.closest(".bkcell").removeClass("initcell");
        }
        scope.$watch('isInitializationCell()', function(newValue, oldValue) {
          if (newValue !== oldValue) {
            if (newValue) {
              element.closest(".bkcell").addClass("initcell");
            } else {
              element.closest(".bkcell").removeClass("initcell");
            }
          }
        });
      }
    };
  });

})();
