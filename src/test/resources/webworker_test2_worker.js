let count = 0;
self.onmessage = (e => {
    count++;
})

for(let i = 0; i < 10000; i++){
    self.postMessage("");
}

for(let i = 0; i < 10000; i++){
    setTimeout(()=>{
        count++
    },0);
}

setTimeout(()=> {
    if(count===20000){
        self.postMessage("")
    }
},800);
