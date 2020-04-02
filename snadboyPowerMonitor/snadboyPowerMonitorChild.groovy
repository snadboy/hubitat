definition(
    name: "SnadBoy Power Monitor Child",
    namespace: "snadboy",
    author: "Daniel Schless",
    description: "Blah, blah, blah ... Parent",
    category: "Convenience",
    parent: "snadboy:SnadBoy Power Monitor",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

preferences { 
    page(name: "pageConfig") // Elimiates the default app name and mode options.
}

def pageConfig() {
    def sectionDiv = "<div style='background-color: aqua; padding: 5px; color: black; border: 1px solid black; font-weight: 500;'>"
    def highlightDiv = "<div style='background-color: MOCCASIN; font-size:80%'>"
    
	dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0) {
        section("${sectionDiv}SnadBoy Power Monitor Child App</div>") {
            paragraph "This app monitors the on/off status of a device based upon its power consumption"
            paragraph "Versioning information goes here"
            label title: "Enter a name for this automation", required: true
        }

        section("${sectionDiv}Power Monitor</div>") {
        	input "powerDevice", "capability.powerMeter", required: true, title: "Choose a device with power monitor capability"
            input "powerOnValue", "number", require: true, defaultValue: 20, title: "On/Off power value (watts)${decorExplain("Device is considered ON when power is at or above this value, and OFF when below")}"
            input "minimumOffDuration", "number", require: true, defaultValue: 30, title: "'Offically Off' delay (seconds)${decorExplain("Number of consecutive seconds power must be less than the 'On/Off power value' to be considered 'Officially Off'")}"
            input "minimumOnDuration", "number", require: true, defaultValue: 12, title: "'Offically On' delay (seconds)${decorExplain("Number of consecutive seconds power must be greater than or equal to the 'On/Off power value' to be considered 'Officially On'")}"
        }
        
        section("${sectionDiv}Notification</div>") {
            input "notificationDevice", "capability.notification", title: "Hubitat Phone or Pushover app to receive 'Official On/Off' messages", multiple: true, required: false
            input "messageTextOn", "text", title: "Text of message to be sent when device turns 'Officially On'${decorExplain('If no text entered, message will not be sent')}"
            input "messageTextOff", "text", title: "Text of message to be sent when device turns 'Officially Off'${decorExplain('If no text entered, message will not be sent')}"
            paragraph '<b>Optional text substitutions within message text:</b><br>' + 
                highlightDiv +
                '$onDay - the day device officially switched on, e.g. Tue<br>' +
                '$onDate - the date device officially switched on, e.g. Tue, Mar 3 1987<br>' + 
                '$onTime - the time device officially switched on, e.g. 4:30:13 PM CT<br>' + 
                '$offDay - the day device officially switched on, e.g. Tue<br>' + 
                '$offDate - the date device officially switched on, e.g. Tue, Mar 3 1987<br>' +
                '$offTime - the time device officially switched on, e.g. 4:30:13 PM CT<br>' +
                '$name - the name of the device, e.g. Zooz Power Switch<br>' +
                '$name - the label of the device, e.g. Laundry Room Dryer</div>'
        }
        
        section("${sectionDiv}Options</div>")
		{
            input "modes", "mode", title: "In which modes do you *NOT* want notifications sent?${decorExplain('Select exclusion mode(s)')}", multiple: true
            input "logEnable", "bool", title: "Enable debug logging for 30 minutes?"
        }
    }
}

String decorExplain(text) {
    return "<div style='font-size:75%;'>${text}</div>"
}

def installed() {
	logMsg("Installed with settings: ${settings}", infoAlt = true)
	initialize()
}

def updated() {
    if (logEnable) {
        state.logEnabled = true
        runIn(30 * 60, stopLog)
        logMsg("Enabling debug logging for 30 minutes")
    } else {
        logMsg("Debug logging not enabled", infoAlt = true)
        state.logEnabled = false
    }
    
	logMsg("Updated with settings: ${settings}", infoAlt = true)
	unsubscribe()
	initialize()
}

def initialize() {
    state.onOff = powerDevice.currentValue("power") < powerOnValue ? 'off' : 'on'

    state.onAt = now()
    state.offAt = now()

  	subscribe(powerDevice, "power", powerHandler)
    logMsg("Initialize:   state.onOff = ${state.onOff}", infoAlt = true)
}

def powerHandler(evt) {
    logMsg("powerHandler event entry - state.onOff = ${state.onOff} - power = ${evt.numericValue} W")
    
    def oldOnOff = state.onOff
    def newOnOff = evt.numericValue < powerOnValue ? "off" : "on"
    
    logMsg("** oldOnOff = ${oldOnOff} -=- newOnOff = ${newOnOff} **")
    if (newOnOff != oldOnOff) {
        def secs = newOnOff == "off" ? minimumOffDuration : minimumOnDuration
        logMsg("Device has changed to '${newOnOff}'-- Setting up 'official' duration timer for ${secs} second(s)")
        runInMillis(secs * 1000, officiallyOnOff, [overwrite: true, data: newOnOff])
    }

    state.onOff = newOnOff
    
    logMsg("powerHandler event exit - state.onOff = ${state.onOff}")
}

def officiallyOnOff(evtOnOff) {
    logMsg("officiallyOnOff event entry - evtOnOff = ${evtOnOff} - state.onOff = ${state.onOff}")

    def currentPower = powerDevice.currentValue("power")
    logMsg("currentPower = ${currentPower} -=- powerOnValue = ${powerOnValue}")
    
    if (evtOnOff == "off") {
//        if (powerDevice.currentValue("power") < powerOnValue) {
            state.onOff = 'off'
            state.offAt = now()
            sendNotification(messageTextOff)
 //       }
    } else {
//        if (powerDevice.currentValue("power") >= powerOnValue) {
            state.onOff = 'on'
            state.onAt = now()
            sendNotification(messageTextOn)
        }
//    }

    logMsg("officiallyOnOff event exit - state.onOff = ${state.onOff}")
}

def sendNotification(msgTxt){
	if (notificationDevice && msgTxt) {
   		if(settings.modes && settings.modes.contains(location.mode))
    	{
            logMsg("Notification not sent because mode, ${location.mode}, has been excluded", infoAlt = true)
            return
        } else {
            def msg = translateMsg(msgTxt)
            logMsg("Sending notification - '${msg}'", infoAlt = true)
            notificationDevice.deviceNotification(msg)
        }
	} 
}

def stopLog() {
    logMsg("*** Stopping debug log ***")
    state.logEnabled = false;
}

def logMsg(logMsg, infoAlt = false) {
    if (state.logEnabled) {
        log.debug "${app.getLabel()}: ${logMsg}"
    } else if (infoAlt) {
        log.info "${app.getLabel()}: ${logMsg}"
    }
}

def logProps(obj, name, prefix) {
    if (!settings.logEnable)
        return

    if (obj != null) {
        logMsg("${prefix}${name} = ${obj}")
        obj.properties.each{ p, v -> logMsg("${prefix}${name}.${p} = ${v}") }
    } else {
        logMsg("${prefix}${name} = NULL")
    }
}

String formatLocalTime(time, String format='EEE, MMM d yyyy @ h:mm:ss a z'){
	def nTime=time
	if("$time".isNumber()){
		Long ltime=time.toLong()
		if(ltime<86400000L)ltime=getTimeToday(ltime)
        // deal with a time in sec (vs. ms)
		if(ltime<Math.round(now()/1000.0D+86400.0D*365.0D))ltime=Math.round(time*1000.0D)
		nTime=new Date(ltime)
	}else if(time instanceof String){
		//get time
		nTime=new Date(stringToTime((String)time))
	}
	if(!(nTime instanceof Date)){
		return (String)null
	}
	def formatter=new java.text.SimpleDateFormat(format)
	formatter.setTimeZone(location.timeZone)
	return formatter.format(nTime)
}

Long stringToTime(dateOrTimeOrString){ // this is convert something to time
	if(dateOrTimeOrString instanceof String){
		Long result

		try{
			result=(new Date()).parse(dateOrTimeOrString)
			return result
		}catch (all0){
		}

		try{
			//get unix time
			if(!(dateOrTimeOrString =~ /(\s[A-Z]{3}((\+|\-)[0-9]{2}\:[0-9]{2}|\s[0-9]{4})?$)/)){
				def newDate=(new Date()).parse(dateOrTimeOrString+' '+formatLocalTime(now(), 'Z'))
				result=newDate
				return result
			}
			result=(new Date()).parse(dateOrTimeOrString)
			return result
		}catch (all){
		}

		try{
			Date tt1=toDateTime(dateOrTimeOrString)
			result=(Long)tt1.getTime()
			return result
		}catch(all3){
		}

		try{
			def tz=location.timeZone
			if(dateOrTimeOrString =~ /\s[A-Z]{3}$/){ // this is not the timezone... strings like CET are not unique.
				try{
					tz=TimeZone.getTimeZone(dateOrTimeOrString[-3..-1])
					dateOrTimeOrString=dateOrTimeOrString.take((Integer)dateOrTimeOrString.size()-3).trim()
				}catch (all4){
				}
			}

			String t0=dateOrTimeOrString?.trim()?: ''
			Boolean hasMeridian=false
			Boolean hasAM=false
			if(t0.toLowerCase().endsWith('am')){
				hasMeridian=true
				hasAM=true
			}
			if(t0.toLowerCase().endsWith('pm')){
				hasMeridian=true
				hasAM=false
			}
			if(hasMeridian)t0=t0[0..-3].trim()

			Long time=timeToday(t0, tz).getTime()//DST

			if(hasMeridian){
				Date t1=new Date(time)
				Integer hr=(Integer)t1.hours
				Integer min=(Integer)t1.minutes
				Boolean twelve=hr==12 ? true:false
				if(twelve && hasAM)hr -= 12
				if(!twelve && !hasAM)hr += 12
				String str1="${hr}".toString()
				String str2="${min}".toString()
				if(hr<10)str1=String.format('%02d', hr)
				if(min<10)str2=String.format('%02d', min)
				String str=str1+':'+str2
				time=timeToday(str, tz).getTime()
			}
			result=time
			return result
		}catch (all5){
		}

		result=(new Date()).getTime()
		return result
	}

	if(dateOrTimeOrString instanceof Date){
		dateOrTimeOrString=(Long)dateOrTimeOrString.getTime()
	}
	if("$dateOrTimeOrString".isNumber()){
		if(dateOrTimeOrString<86400000)dateOrTimeOrString=getTimeToday(dateOrTimeOrString)
		return dateOrTimeOrString
	}
	return 0L
}

def translateMsg(msg) {
    msg = msg.replaceAll(/[$]onDay/, formatLocalTime (state.onAt, "EEE"))
    msg = msg.replaceAll(/[$]onDate/, formatLocalTime (state.onAt, "EEE, MMM d yyyy"))
    msg = msg.replaceAll(/[$]onTime/, formatLocalTime (state.onAt, "h:mm:ss a z"))
    msg = msg.replaceAll(/[$]offDay/, formatLocalTime (state.offAt, "EEE"))
    msg = msg.replaceAll(/[$]offDate/, formatLocalTime (state.offAt, "EEE, MMM d yyyy"))
    msg = msg.replaceAll(/[$]offTime/, formatLocalTime (state.offAt, "h:mm:ss a z"))
    msg = msg.replaceAll(/[$]label/, powerDevice.label)
    msg = msg.replaceAll(/[$]name/, powerDevice.name)
    return msg
}

