
self.onmessage = (e => {
    if(e.data == 'hello'){
      postMessage('hiya!');
    }
})
