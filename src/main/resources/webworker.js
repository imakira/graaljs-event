class Worker {
    onmessage = null;
    onerror = null;
    onclose = null;
    _eventContext = null;

    constructor(url, options) {
        this._eventContext = _newEContext(this, url, options);
    }

    postMessage(msg) {
        this._postMessage(
            JSON.stringify({
                data: msg,
                type: 'message',
                timeStamp: Date.now()
            }));
    }

    terminate() {

    }

    _onmessage(msg_str) {
        if (this.onmessage) {
            this.onmessage.call(this,
                {
                    ...JSON.parse(msg_str),
                    target: this,
                    currentTarget: this,
                });
        }
    }
}

globalThis.Worker = Worker;
globalThis._setup_worker = function() {
    globalThis.postMessage = function (msg) {
        globalThis._postMessage(
            JSON.stringify({
                data: msg,
                type: 'message',
                timeStamp: Date.now()
            }));
    }

    globalThis._onmessage = function (msg) {
        if(globalThis.onmessage) {
            globalThis.onmessage(JSON.parse(msg));
        }
    }
}
