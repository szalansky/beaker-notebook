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
  beaker.bkoFactory('FooService', [function() {
    return {
      getFoo: function() {
        return "Bar";
      }
    }
  }]);

  beaker.bkoDirective('Foo', ["FooService", function(FooService) {
    return {
      template: "<div class='MyFooClass'>I say {{ getText() }}</div>" +
      "<div class='MyFooClass2'>{{ message }}</div><button class='MyButton' ng-click='changeMessage()'>AAA</button>",
      controller: function($scope) {
        $scope.getText = function() {
          return "Text = " + FooService.getFoo();
        };
        $scope.message = "Hello, world";
        $scope.changeMessage = function() {
          $scope.message = "Hola, world";
        };
      }
    };
  }]);
})();
