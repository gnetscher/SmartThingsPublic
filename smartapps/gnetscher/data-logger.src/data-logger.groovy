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
    section("Monitor the stove temperature") {
        input "stoveTemperatureSensor", "capability.temperatureMeasurement", title: "Temperature?", multiple: true
    }
    section("Monitor gas from the stove") {
    input "stoveCOSensor", "capability.carbonMonoxideDetector", title: "CO?", multiple: true
    }
    section("Monitor the shower") {
        input "showerTemperatureSensor", "capability.temperatureMeasurement", title: "Temperature?", multiple: true
        input "showerHumiditySensor", "capability.relativeHumidityMeasurement", title: "Humidity?", multiple: true
    }
    section("Monitor water overflow") {
        input "overflowSensor", "capability.waterSensor", title: "Water?", multiple: true
    }
}

def installed() {
	setup()
}

def updated() {
	unsubscribe()
	setup()
}

def newDataHandler(evt) {
    // maintain running list of data updates    
    def entry = [now(), evt.value]   
    state.dataMap[evt.displayName] << entry
    log.debug "$state.dataMap"
}

def dataDump(evt) {
	// notify that cron is executing
    sendPush("Cron executing")

    // format data
    def params = [
	    uri: "http://nestsense.banatao.berkeley.edu:8080",
        body: state.dataMap
    ]
    
    // post data to server
    try {
    	httpPostJson(params) { resp ->
        log.debug "response data: ${resp.data}"
        log.debug "response contentType: ${resp.contentType}"
    	}
    } catch (e) {
        log.debug "something went wrong: $e"
    }
    
    // clean up
	setUpDataMap()
}

def setUpDataMap() {
	// keep all sensors in sensorAttributeMap
    state.sensorAttributeMap = [(stoveTemperatureSensor): "temperature", 
    					 	   (showerTemperatureSensor): "temperature",
                          	   (showerHumiditySensor)	: "humidity",
                       	       (stoveCOSensor)			: "carbonMonoxide",
                          	   (overflowSensor)			: "water"]

    // keep all data in dataMap
    state.dataMap = [location: "$location.name"]	
    state.sensorAttributeMap.each { entry -> 
        // maintain data lists accross instances
        for (name in entry.key.displayName) {
	        state.dataMap[name] = []
        }
    }
}

def setup() {
    setUpDataMap()

    // run handler whenever data changes
    state.sensorAttributeMap.each { entry -> 
    	subscribe(entry.key, entry.value, newDataHandler)
    }   
   
    // schedule data dump at 3AM daily
    // test with http://www.cronmaker.com/
	schedule("0 0 3 1/1 * ? *", dataDump)
}