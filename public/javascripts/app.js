// Define the `puppetModuleApp` module
var puppetModuleApp = angular.module('puppetModuleApp', []);

// Define the `RequirementController` controller
puppetModuleApp.controller('RequirementController', function ($scope, $http) {
  
  $scope.existingRequirements = [];
  $scope.newRequirements = [];
  
  $scope.puppetDbUrl = "http://192.168.1.19:8080/pdb";
  $scope.message = "";
  $scope.loading = false;
  $scope.applying = false;
  $scope.removing = false;
  $scope.existingAll = false;
  $scope.newAll = false;
  
  $scope.scenarios = [];
  $scope.scenario = "";
  
  // Initializes the structures
  $scope.init = function () {
	  $http({
          method: 'GET',
          url: '/scenarios'
      }).success(function(data){
    	  $scope.scenarios = data;
      }).error(function(){
          alert("Error getting scenarios");
      });
  }
  
  $scope.init();
  
  // Services
  $scope.isSelectAllExisting = function() {
	  angular.forEach($scope.existingRequirements, function (item) {
	    item.selected = $scope.existingAll;
	  });
	}
  
  $scope.isSelectAllNew = function() {
	  angular.forEach($scope.newRequirements, function (item) {
	    item.selected = $scope.newAll;
	  });
	}
  
  $scope.getRequirements = function() {
	  
	  if($scope.scenario === "") {
		  alert("Choose a scenario!!")
		  return;
	  }
	  
	  $scope.loading = true;
	  $http({
          method: 'GET',
          url: '/requirements?id=' + $scope.scenario.id + '&url=' + $scope.puppetDbUrl
      }).success(function(data){
    	  $scope.existingRequirements = data.existingRequirements;
    	  $scope.newRequirements = data.newRequirements;
      }).error(function(){
    	  alert("Error getting requirements");
      }).finally(function(){
    	  $scope.loading = false;
      });
  }
  
  $scope.applyRequirements = function() {
	  
	  if($scope.scenario === "") {
		  alert("Choose a scenario!!")
		  return;
	  }
	  
	  $scope.applying = true;
	  
	  var reqs = $scope.newRequirements.filter(function (el) { return el.selected });
	  
	  $http({
          method: 'POST',
          url: '/apply?id=' + $scope.scenario.id + '&url=' + $scope.puppetDbUrl,
          data: reqs
      }).success(function(data){
    	  $scope.existingRequirements = data.existingRequirements;
    	  $scope.newRequirements = data.newRequirements;
      }).error(function(){
    	  alert("Error applying requirements");
      }).finally(function(){
    	  $scope.applying = false;
      });
  }
  
  $scope.removeRequirements = function() {
	  
	  if($scope.scenario === "") {
		  alert("Choose a scenario!!")
		  return;
	  }
	  
	  $scope.removing = true;
	  
	  var reqs = $scope.existingRequirements.filter(function (el) { return el.selected });
	  var ids = reqs.map(function (el) { return el.id });
	  
	  $http({
          method: 'POST',
          url: '/remove?id=' + $scope.scenario.id + '&url=' + $scope.puppetDbUrl,
          data: ids
      }).success(function(data){
    	  $scope.existingRequirements = data.existingRequirements;
    	  $scope.newRequirements = data.newRequirements;
      }).error(function(){
    	  alert("Error removing requirements");
      }).finally(function(){
    	  $scope.removing = false;
      });
  }

});

// Filters
puppetModuleApp.filter('printServices', function () {
	// print the services in a pretty format
	function prettyPrint(json) {
		var services = "any";
		if (json && json.length > 0) {
			services = $.map(json, function(v){
				return v.name + " -> " + v.ports;
			}).join(', ');
		}
		
		return services;
    }
	
	return prettyPrint;
});

puppetModuleApp.filter('printApplications', function () {

	function prettyPrint(json) {
		var apps = "";
		
		if (json && json.length > 0) {
			apps = $.map(json, function(v){
				return v;
			}).join(', ');
		}
		
		return apps;
    }
	
	return prettyPrint;
});

puppetModuleApp.filter('actionClass', function () {
	
	function prettyPrint(action) {
		var clazz = "text-success";
		if (action === "deny") {
			clazz = "text-danger"
		}
		
		return clazz;
    }
	
	return prettyPrint;
});
