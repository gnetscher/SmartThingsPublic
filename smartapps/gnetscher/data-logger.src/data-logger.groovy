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
        input "Kitchen_Stove_0", "capability.temperatureMeasurement", title: "Stove sensor?", 
        	  multiple: true, required: false
    }
    section("Monitor gas from the stove") {
    	input "Kitchen_Stove_1", "capability.carbonMonoxideDetector", title: "CO?", 
        	  multiple: true, required: false
    }
    section("Monitor the shower") {
        input "Bathroom_Shower_0", "capability.temperatureMeasurement", title: "Shower sensor?", 
	          multiple: true, required: false
    }
    section("Monitor water overflow") {
        input "Bathroom_Sink_0", "capability.waterSensor", title: "Water sensor?", 
        	  multiple: true, required: false
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
    def att = evt.description.split(':')[0]
    def entry = [now(), evt.value]   
	try {
	    state.dataMap["data"]["$evt.displayName"]["$att"] << entry
    } catch (e) {
    	state.dataMap["data"]["$evt.displayName"]["$att"] = entry
    }
    log.debug "$state.dataMap"
}

def dataDump(evt) {
	// notify that cron is executing
    sendPush("Cron executing")

    // format data
    def params = [
	    uri: "http://nestsense.banatao.berkeley.edu:9000/upload_sensors_data",
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
	// prepare active sensors in sensorAttributeMap
    state.sensorAttributeMap = [(Kitchen_Stove_0)	: ["temperature"], 
    					 	   (Bathroom_Shower_0)	: ["temperature", "humidity"],
                       	       (Kitchen_Stove_1)	: ["carbonMonoxide"],
                          	   (Bathroom_Sink_0)	: ["water"]]
	def tempMap = [:]
    state.sensorAttributeMap.each { entry ->
    	if (entry.key != null) {
        	tempMap[entry.key] = entry.value
        }
    }
    state.sensorAttributeMap = tempMap
    log.debug state.sensorAttributeMap
                    
    /** keep all data in dataMap with structure:
	*		config
    *		--> homeID
    *		--> timezone
	*	 	data
    *		-->	sensor
    *			-->	attribute
    *				--> entries
    */
    def timezone = Calendar.getInstance().getTimeZone().getID()
    state.dataMap = ["config": ["home_id": "$location.name", "timezone": timezone],
    				 "data": [:]]
    state.sensorAttributeMap.each { entry -> 
        for (name in entry.key.displayName) { 
            log.debug state.dataMap
            def attMap = [:]
            for (att in entry.value) {
                attMap["$att"] = []
            }
            state.dataMap["data"] << ["$name": attMap]
        }
    }
    log.debug state.dataMap

}

def setup() {
    setUpDataMap()

    // run handler whenever data changes
    state.sensorAttributeMap.each { entry -> 
        for (att in entry.value) {
            subscribe(entry.key, att, newDataHandler)
        }
    }   
   
    // schedule data dump at 2AM daily (9 below is for UTC-0)
    // test with http://www.cronmaker.com/
	schedule("0 0 9 1/1 * ? *", dataDump)
}