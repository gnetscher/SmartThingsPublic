/**
 *  Data Logger
 *
 *  Copyright 2016 George Netscher
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Data Logger",
    namespace: "gnetscher",
    author: "George Netscher",
    description: "Log data from home sensors",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	section("Monitor the stove") {
		input "stoveTemperatureSensor", "capability.temperatureMeasurement", required: true, title: "Where?"
	}
	section("Monitor the shower") {
		input "showerTemperatureSensor", "capability.temperatureMeasurement", required: true, title: "Where?"
	}
}

def installed() {
	setup()
}

def updated() {
	unsubscribe()
	setup()
}

// TODO: add other sensors: shower_humidity, carbon monoxide, faucets
// TODO: add proper processing on the server

def temperatureHandler(evt) {
    // maintain running list of temperature updates
    log.debug "$evt.displayName"
    def tempEntry = [now(), evt.value]
    log.debug "new temp data: $tempEntry"

	if (evt.displayName == "stoveTemperatureSensor") {
    	state.stoveTempData << tempEntry
    } else if (evt.displayName == "showerTemperatureSensor") {
    	state.showerTempData << tempEntry
    }
}

def humidityHandler(evt) {}

def dataDump(evt) {
    log.debug "stoveTempData: $state.stoveTempData"
    log.debug "showerTempData: $state.showerTempData"
    // TODO: replace home1 with ID for this home
    
    // format data
    def params = [
    uri: "http://nestsense.banatao.berkeley.edu:8080",
        body: [
        	loc         : "$location.name",
            stove_temp  : "$state.stoveTempData",
            shower_temp : "$state.showerTempData"
        ]
    ]
    
    // post data to server
    try {
    	httpPost(params) { resp ->
        log.debug "response data: ${resp.data}"
        log.debug "response contentType: ${resp.contentType}"
    	}
    } catch (e) {
        log.debug "something went wrong: $e"
    }
}

def setup() {
	// run temperatureHandler whenever temperature changes
	subscribe(stoveTemperatureSensor, "temperature", temperatureHandler)
	subscribe(showerTemperatureSensor, "temperature", temperatureHandler)
    // maintain tempData list accross instances
    state.stoveTempData = []
    state.showerTempData = []
    state.showerHumidData = []    
    
    // schedule data dump at 3AM daily
    // test with http://www.cronmaker.com/
	schedule("0 0 3 1/1 * ? *", dataDump)
}