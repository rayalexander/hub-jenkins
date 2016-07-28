/*******************************************************************************
 * Copyright (C) 2016 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

function customWrapperCreateProject(method, withVars, button,  projectFieldName, versionFieldName) {

	validateButton(method, withVars, button);

	var projectField = getFieldByName(projectFieldName);
	var versionField = getFieldByName(versionFieldName);

	// wait 1000 ms before running the checks
	// otherwise it happens too quick and shows that the project doesnt
	// exist yet
	setTimeout(function() {
		checker(projectField);
		checker(versionField);
	}, 1000);
	
	var sameAsBuildWrapper = getFieldByName('_.sameAsBuildWrapper');
	if (sameAsBuildWrapper && sameAsBuildWrapper.checked) {
		// We found the Cli scan checkbox and it is checked to use the same configuration as the Build wrapper
		// So we run the check on the cli fields as well
		
		var hubProjectName = getFieldByName('_.hubProjectName');
		var hubProjectVersion = getFieldByName('_.hubProjectVersion');
		
		setTimeout(function() {
			checker(hubProjectName);
			checker(hubProjectVersion);
		}, 1000);
	}
}


function enableSameAsPostBuildScan(onload, projectFieldName, versionFieldName, phaseFieldName, distFieldName, messageAreaId, wrapperCheckBoxName) {

	var sameAsBuildWrapper = getFieldByName('_.sameAsBuildWrapper');
	if (sameAsBuildWrapper && sameAsBuildWrapper.checked) {
		// The Build wrapper and the Cli are both checked to use the other configuration
		// This is obviously an issue so we log an error to the screen
		
		enableWrapperFields(projectFieldName, versionFieldName, phaseFieldName, distFieldName);
		addTextToMessageArea("The Post-build Action Hub Integration is configured to use the Build Environment configuration!", messageAreaId);
		return;
	}
		var hubProjectName = getFieldByName('_.hubProjectName');
		var hubProjectVersion = getFieldByName('_.hubProjectVersion');
		var hubVersionPhase = getFieldByName('_.hubVersionPhase');
		var hubVersionDist = getFieldByName('_.hubVersionDist');

		var hubWrapperProjectName = getFieldByName(projectFieldName);
		var hubWrapperProjectVersion = getFieldByName(versionFieldName);
		var hubWrapperVersionPhase = getFieldByName(phaseFieldName);
		var hubWrapperVersionDist = getFieldByName(distFieldName);

		// Only run this if the scan has been configured
		if ((hubProjectName) && (hubProjectVersion) && (hubVersionPhase)
				&& (hubVersionDist)) {
			addOnBlurToScanFields(projectFieldName, versionFieldName, phaseFieldName, distFieldName, wrapperCheckBoxName);
				
			//We disable the wrapper fields since we want to use the scan fields
			disableWrapperFields(projectFieldName, versionFieldName, phaseFieldName, distFieldName);

			hubWrapperProjectName.value = hubProjectName.value;
			hubWrapperProjectVersion.value = hubProjectVersion.value;
			hubWrapperVersionPhase.value = hubVersionPhase.value;
			hubWrapperVersionDist.value = hubVersionDist.value;

			if (!(onload)) {
				// Only check if not onload
				// These automatically get checked onload
				setTimeout(function() {
					checker(hubWrapperProjectName);
					checker(hubWrapperProjectVersion);
				}, 1000);
			}
		} else {
			//The scan is not configured so we cant use the same configuration as it
			// so we log the error for the user
			
			addTextToMessageArea("The Post-build Action 'Black Duck Hub Integration' is not configured for this Job!", messageAreaId);
		}
}

function disableSameAsPostBuildScan(onload, messageAreaId, projectFieldName, versionFieldName, phaseFieldName, distFieldName) {
	//We enable the wrapper fields since we no longer want to use the scan fields
	enableWrapperFields(projectFieldName, versionFieldName, phaseFieldName, distFieldName);

	
	// We remove the appropriate error messages from the UI
	var sameAsPostBuildScanMessageArea = document.getElementById(messageAreaId);
	sameAsPostBuildScanMessageArea.className = '';
	while (sameAsPostBuildScanMessageArea.firstChild) {
		sameAsPostBuildScanMessageArea
				.removeChild(sameAsPostBuildScanMessageArea.firstChild);
	}


}

function enableWrapperFields(projectFieldName, versionFieldName, phaseFieldName, distFieldName) {
	var hubWrapperProjectName = getFieldByName(projectFieldName);
	var hubWrapperProjectVersion = getFieldByName(versionFieldName);
	var hubWrapperVersionPhase = getFieldByName(phaseFieldName);
	var hubWrapperVersionDist = getFieldByName(distFieldName);

	// Make sure the fields are no longer read only or disabled
	hubWrapperProjectName.readOnly = false;
	hubWrapperProjectVersion.readOnly = false;
	hubWrapperVersionPhase.disabled = false;
	hubWrapperVersionDist.disabled = false;
	
	//Remove the readonly css class we added to the fields
	hubWrapperProjectName.className = hubWrapperProjectName.className.replace(/ bdReadOnly/g,"");
	hubWrapperProjectVersion.className = hubWrapperProjectVersion.className.replace(/ bdReadOnly/g,"");
	hubWrapperVersionPhase.className = hubWrapperVersionPhase.className.replace(/ bdReadOnly/g,"");
	hubWrapperVersionDist.className = hubWrapperVersionDist.className.replace(/ bdReadOnly/g,"");

}

function disableWrapperFields(projectFieldName, versionFieldName, phaseFieldName, distFieldName) {
	var hubWrapperProjectName = getFieldByName(projectFieldName);
	var hubWrapperProjectVersion = getFieldByName(versionFieldName);
	var hubWrapperVersionPhase = getFieldByName(phaseFieldName);
	var hubWrapperVersionDist = getFieldByName(distFieldName);

	// Make sure the fields are read only or disabled
	hubWrapperProjectName.readOnly = true;
	hubWrapperProjectVersion.readOnly = true;
	hubWrapperVersionPhase.disabled = true;
	hubWrapperVersionDist.disabled = true;
	
	//Add the readonly css class to the fields	
	hubWrapperProjectName.className = hubWrapperProjectName.className + ' bdReadOnly';
	hubWrapperProjectVersion.className = hubWrapperProjectVersion.className + ' bdReadOnly';
	hubWrapperVersionPhase.className = hubWrapperVersionPhase.className + ' bdReadOnly';
	hubWrapperVersionDist.className = hubWrapperVersionDist.className + ' bdReadOnly';
}

function addOnBlurToScanFields(projectFieldName, versionFieldName, phaseFieldName, distFieldName, wrapperCheckBoxName) {
	doubleOnBlur(projectFieldName, versionFieldName, '_.hubProjectName', wrapperCheckBoxName);
	doubleOnBlur(versionFieldName, projectFieldName, '_.hubProjectVersion', wrapperCheckBoxName);
	singleOnBlur(phaseFieldName, '_.hubVersionPhase', wrapperCheckBoxName);
	singleOnBlur(distFieldName, '_.hubVersionDist', wrapperCheckBoxName);

}

function addTextToMessageArea(txt, messageAreaId){
	var messageArea = document.getElementById(messageAreaId);
	if(messageArea.className.indexOf('error') == -1){
		messageArea.className = 'error';
	}
	if((messageArea.firstChild)){
		if(messageArea.firstChild.innerHtml == txt){
			return;
		} else{
			messageArea.firstChild.innerHtml = messageArea.firstChild.innerHtml + " " + txt;
			return;
		}
	}
	var newScanSpan = document.createElement('span');
	newScanSpan.innerHTML = txt;
	messageArea.appendChild(newScanSpan);
	return;
}
