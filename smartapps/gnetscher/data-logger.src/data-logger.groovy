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
		input "showerHumiditySensor", "capability.relativeHumidityMeasurement", required: true, title: "Where?"
	}
}

def installed() {
	setup()
}

def updated() {
	unsubscribe()
	setup()
}

// TODO: add other sensors: carbon monoxide, faucets
// TODO: add proper processing on the server

def newDataHandler(evt) {
    // maintain running list of data updates
    def data = evt.description.split(':')
    def type = data[0]
    def value= data[1]
    
    def entry = [now(), value]
    log.debug "$evt.description" << " from " << "$evt.displayName" << " on " << "$evt.date"

    if (type == "temperature") {
        if (evt.displayName == "stoveTemperatureSensor") {
            state.stoveTempData << entry
        } else if (evt.displayName == "showerTemperatureSensor") {
            state.showerTempData << entry
        }
    } else if (type == "humidity") {
    	state.showerHumidData << entry
    }
}

def dataDump(evt) {
    // format data
    def params = [
    uri: "http://nestsense.banatao.berkeley.edu:8080",
        body: [
        	location           : "$location.name",
            stove_temperature  : "$state.stoveTempData",
            shower_temperature : "$state.showerTempData",
            shower_humidity    : "$state.showerHumidData"
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
	// run handler whenever data changes
	subscribe(stoveTemperatureSensor, "temperature", newDataHandler)
	subscribe(showerTemperatureSensor, "temperature", newDataHandler)
    subscribe(showerHumiditySensor, "humidity", newDataHandler)
    
    // maintain data lists accross instances
    state.stoveTempData = []
    state.showerTempData = []
    state.showerHumidData = []    
    
    // schedule data dump at 3AM daily
    // test with http://www.cronmaker.com/
	schedule("0 0 3 1/1 * ? *", dataDump)
}