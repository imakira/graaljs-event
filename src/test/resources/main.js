const worker = new Worker(import.meta.url + '/../worker.js');

worker.onmessage = (e => {
    console.log(e.data)  // "hiya!"
});

worker.postMessage('hello');
