const worker = new Worker(import.meta.url + '/../webworker_test2_worker.js');

globalThis.count = 0;

worker.onmessage = (e => {
    globalThis.count++;
})

for(let i = 0; i < 10000; i++){
    setTimeout(()=> {globalThis.count++}, 0);
    worker.postMessage("");
}
