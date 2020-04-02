/*
 *	SnadBoy Power Monitor (Parent)
 *
 *	Author: Daniel Schless
 * 
 */

definition(
    name: "SnadBoy Power Monitor",
    namespace: "snadboy",
    singleInstance: true,
    author: "Daniel Schless",
    description: "Blah, blah, blah ... Parent",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	return dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        if(!state.PowerInstalled) {
            section("Hit Done to install SnadBoy Power Parent") {
        	}
        }
        else {
        	section("Create a new SnadBoy Power Monitor Instance.") {
            	app(name: "childApps", appName: "SnadBoy Power Monitor Child", namespace: "snadboy", title: "New SnadBoy Power Monitor Instance", multiple: true)
        	}
    	}
    }
}

def installed() {
    state.PowerInstalled = true
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
}