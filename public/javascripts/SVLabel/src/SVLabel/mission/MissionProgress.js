/**
 * MissionProgress module.
 * Todo. Rename this... Probably some of these features should be moved to status/StatusFieldMission.js
 * @returns {{className: string}}
 * @constructor
 * @memberof svl
 */
function MissionProgress (svl, gameEffectModel, missionModel, modalModel, neighborhoodModel, missionContainer, neighborhoodContainer, taskContainer) {
    var self = this;

    var _gameEffectModel = gameEffectModel;
    var _missionModel = missionModel;
    var _modalModel = modalModel;


    _missionModel.on("MissionProgress:update", function (parameters) {
        var mission = parameters.mission,
            neighborhood = parameters.neighborhood;
        self.update(mission, neighborhood);
    });

    /**
     * Finish the mission.
     * @param mission
     */
    this._completeTheCurrentMission = function (mission, neighborhood) {
        mission.complete();
        _gameEffectModel.playAudio({audioType: "yay"});
        _gameEffectModel.playAudio({audioType: "applause"});

        // Update the neighborhood status
        if ("labelContainer" in svl) {
            var regionId = neighborhood.getProperty("regionId");
            var count = svl.labelContainer.countLabels(regionId);
            svl.statusFieldNeighborhood.setLabelCount(count);
        }

        _missionModel.completeMission(mission);
    };

    /**
     * @param mission Next mission
     * @param neighborhood Current neighborhood
     */
    function _showNextMission (mission, neighborhood) {
        var label = mission.getProperty("label");
        var parameters = { badgeURL: mission.getProperty("badgeURL") };

        if (label == "distance-mission") {
            parameters.distance = mission.getProperty("distance");
            _modalModel.trigger("ModalMission:setMission", { mission: mission, neighborhood: neighborhood, parameters: parameters, callback: null });  // Todo. I should not trigger it here. Call a function.
        } else if (label == "area-coverage-mission") {
            parameters.coverage = mission.getProperty("coverage");
            // modalMission.setMission(mission, neighborhood, parameters);
            _modalModel.trigger("ModalMission:setMission", { mission: mission, neighborhood: neighborhood, parameters: parameters, callback: null });

        } else {
            console.warn("Debug: It shouldn't reach here.");
        }
    }

    this._checkMissionComplete = function (mission, neighborhood) {
        // var _callback = function () {
        //     var neighborhoodId = neighborhood.getProperty("regionId");
        //     var nextMission = missionContainer.nextMission(neighborhoodId);
        //     var movedToANewRegion = false;
        //
        //     // Check if the next mission is null and, if so, get a mission from other neighborhood.
        //     // Note. Highly unlikely, but this could potentially be an infinate loop
        //     while (!nextMission) {
        //         // If not more mission is available in the current neighborhood, get missions from the next neighborhood.
        //         var availableRegionIds = missionContainer.getAvailableRegionIds();
        //         var newRegionId = neighborhoodContainer.getNextRegionId(neighborhoodId, availableRegionIds);
        //         nextMission = missionContainer.nextMission(newRegionId);
        //         movedToANewRegion = true;
        //     }
        //
        //
        //     missionContainer.setCurrentMission(nextMission);
        //     _showNextMission(nextMission, neighborhood);
        //
        //     if (movedToANewRegion) {
        //         neighborhoodContainer.moveToANewRegion(newRegionId);
        //         taskContainer.fetchTasksInARegion(newRegionId, function () {
        //             // Jump to the new location.
        //             var newTask = taskContainer.nextTask(task);
        //             taskContainer.setCurrentTask(newTask);
        //             svl.map.moveToTheTaskLocation(newTask);
        //
        //         }, false);  // Fetch tasks in the new region
        //     }
        // };


        if (mission.getMissionCompletionRate() > 0.999) {
            this._completeTheCurrentMission(mission, neighborhood);
            this._updateTheCurrentMission(mission, neighborhood);

            _modalModel.updateModalMissionComplete(mission, neighborhood);
            _modalModel.showModalMissionComplete();
        }
    };

    this._updateTheCurrentMission = function (currentMission, currentNeighborhood) {
        var currentNeighborhoodId = currentNeighborhood.getProperty("regionId");
        var nextMission = missionContainer.nextMission(currentNeighborhoodId);
        missionContainer.setCurrentMission(nextMission);
        var nextNeighborhood = neighborhoodContainer.get(nextMission.getProperty("regionId"));

        // If the current neighborhood is different from the next neighborhood
        if (currentNeighborhood.getProperty("regionId") != nextNeighborhood.getProperty("regionId")) {
            this._updateTheCurrentNeighborhood(nextNeighborhood);
        }
    };

    /**
     * Toco. This method should be moved to NeighborhoodContainer.
     * @param neighborhood
     * @private
     */
    this._updateTheCurrentNeighborhood = function (neighborhood) {
        var neighborhoodId = neighborhood.getProperty("regionId");
        neighborhoodContainer.setCurrentNeighborhood(neighborhood);
        neighborhoodModel.moveToANewRegion(neighborhoodId);

        taskContainer.fetchTasksInARegion(neighborhoodId, function () {
            // Jump to the new location.
            var newTask = taskContainer.nextTask();
            taskContainer.setCurrentTask(newTask);
            svl.map.moveToTheTaskLocation(newTask);
        }, false);  // Fetch tasks in the new region
    };


    /**
     * This method updates the mission completion rate and its visualization.
     */
    this.update = function (currentMission, currentRegion) {
        if (svl.isOnboarding()) return;
        var completionRate = currentMission.getMissionCompletionRate();
        svl.statusFieldMission.printCompletionRate(completionRate);
        svl.statusFieldMission.updateMissionCompletionBar(completionRate);
        this._checkMissionComplete(currentMission, currentRegion);
    };
}
