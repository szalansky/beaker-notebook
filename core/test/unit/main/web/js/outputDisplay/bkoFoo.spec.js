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
describe("Beaker OuputDisplay Foo", function() {

  var fooService;
  var bkoFoo;
  var myScope;
  var element;

  // 'beforeEach' will run before each 'it', see below
  beforeEach(module("M_bkOutputDisplay"));
  beforeEach(inject(function(_outputDisplayFactory_, _outputDisplayService_, $compile, $rootScope) {

    // if you defined a factory with beaker.bkoFactory
    // you can retrieve it like this:
    fooService = _outputDisplayService_.get("FooService");

    // if you defined a OutputDisplay with beaker.bkoDirective
    // you can retrieve it like this
    bkoFoo = _outputDisplayFactory_.getImpl("Foo");

    // however, bkoFoo is just the definition, you need to instantiate it by creating an element
    element = angular.element(
        "<bk-output-display model='outputDisplayModel' type='Foo'></bk-output-display>");

    // and you need to get a new scope that is going to be associated with the OutputDisplay
    var scope = $rootScope.$new();

    // you can assign properties to the scope, so it can be used in your OutputDisplay
    // for example, you can generate some table output and assign it to outputModel
    // note the string 'outputDisplayModel' matches the 'model' attribute above.
    var outputModel = { column: 1234 };
    scope.outputDisplayModel = {
      getCellModel: function() {
        return outputModel;
      }
    };

    // now compile it, Angular creates the OutputDisplay
    $compile(element)(scope);

    // $digest ensures things are refreshed
    $rootScope.$digest();

    // the OutputDisplay is going to create isolated scope(s) that is actually different from
    // the scope we just create with $rootScope.$new().
    // Here is how you can get the actual scope visible to your OutputDisplay.
    // myScope here is essentially the '$scope' you have in the bkoDirective controller,
    // or 'scope' in the link function.
    // Note, because in the template we say: model='outputDisplayModel'
    // myScope.model is expected to be scope.outputDisplayModel
    myScope = element.find('.MyFooClass').scope();
  }));

  describe("FooService", function() {
    it("should getFoo", function() {
      // try change "Bar" to something else and see the test fail
      // also try to change the return value of getFoo in bkoFoo.js and see what happens
      expect(fooService.getFoo()).toEqual("Bar");
    });
  });

  describe("bkoFoo", function () {
    it("should get cell model", function() {
      expect(myScope.model.getCellModel().column).toBe(1234);
    });

    it("should getText", function() {
      expect(myScope.getText()).toEqual("Text = Bar");
    });

    it("should show div with MyFooClass", function() {
      expect(element.find('.MyFooClass').html()).toEqual("I say Text = Bar");
    });

    it("should update MyFooClass2 upon button click", function() {
      expect(element.find('.MyFooClass2').html()).toEqual("Hello, world");
      element.find('.MyButton').click();
      expect(element.find('.MyFooClass2').html()).toEqual("Hola, world");
    });
  });

});