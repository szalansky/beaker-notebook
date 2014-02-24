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
 * bkCellPluginManager
 */
(function () {
    'use strict';
    var M_bkOutputDisplayManager = angular.module('M_bkOutputDisplayManager', [
        'M_bkUtils',
        'M_bkHelper'  // This is only for ensuring that window.bkHelper is set, don't use bkHelper directly
    ]);
    M_bkOutputDisplayManager.factory('bkOutputDisplayManager', function (bkUtils) {
        var _outputDisplays = {};
        return {
            reset: function () {
                var self = this;
                $.get('/beaker/rest/util/preloadOutputDisplays')
                    .done(function (urls) {
                        urls.forEach(self.load);
                    });
            },
            load: function (url) {
                return bkUtils.loadModule(url);
            }
        };
    });
})();