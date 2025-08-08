// core/IOsocket.js
const IO = require('socket.io');
const geoip = require('geoip-lite');

module.exports = function initSocketServer(serverPort, clientManager, config) {
    const io = IO.listen(serverPort);
    io.sockets.pingInterval = 30000;

    io.on('connection', (socket) => {
        socket.emit('welcome');

        const params = socket.handshake.query || {};
        const rawIP = socket.request.connection.remoteAddress || '0.0.0.0';
        const clientIP = rawIP.substring(rawIP.lastIndexOf(':') + 1);
        const clientGeo = geoip.lookup(clientIP) || {};

        clientManager.clientConnect(socket, params.id || 'unknown', {
            clientIP,
            clientGeo,
            device: {
                model: params.model || 'Unknown',
                manufacture: params.manf || 'Unknown',
                version: params.release || 'Unknown'
            }
        });

        if (config.debug) {
            const originalOnevent = socket.onevent;
            socket.onevent = function (packet) {
                const args = packet.data || [];
                originalOnevent.call(this, packet);
                packet.data = ['*', ...args];
                originalOnevent.call(this, packet);
            };

            socket.on('*', (event, data) => {
                console.log(`[DEBUG SOCKET] Event: ${event}`);
                console.log(data);
            });
        }
    });

    return io;
};
