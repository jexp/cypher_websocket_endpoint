var WebSocket = require('ws')
  , ws = new WebSocket('http://localhost:5555')
  , msgpack = require('msgpack-js');

// todo connection concept
// multiplex/demultiplex concurrent queries
// mask or protocol-extension
ws.on('open', function() {
    // var query = "start n=node(0) return n";
    var query = "start n=node(0) match p=n-[r:KNOWS]->m return p,n,r,m,nodes(p) as nodes, rels(p) as rels,length(p) as length";
    //var query = "start n=node(0) match n-[r:KNOWS]->m return r";
    var data = msgpack.encode(query);


    var time=new Date();
    var count=0;
    var max=100;
    var bytes = 0;
    var interval = 0;
    ws.on('message', function(message,flags) {
        //console.log(flags);
        var buffer=flags.buffer; // new Buffer(message);
        bytes += buffer.length;
		var decoder = new msgpack.Decoder(buffer);
		do {
			var value = decoder.parse();
	        // console.log('received',count, JSON.stringify(value));
		} while(decoder.offset !== buffer.length);
		count++;
        if ((count % 100) == 0) console.log(count);
        if (count==max) {
//            clearInterval(interval);
            ws.close(); // usually it would keep running
        } else {
            ws.send(data,{binary:true,fin:true});
        }
    });
    ws.on('error',console.log);
    ws.on('close',function() {
       console.log("closing count",count," start ",time," end ",new Date(),new Date()-time," ms","bytes",bytes);
    })
    ws.send(data,{binary:true,fin:true});
//    for (var i=0;i<max;i++) {
//        // console.log(i);
//        ws.send(data,{binary:true,fin:true});
//    }
//    interval = setInterval(function() {
//        if (count<max) return;
//        clearInterval(interval);
//        ws.close(); // usually it would keep running
//       } ,250);
});

function query(query, cb) {
    var data = msgpack.encode(query);
    ws.send(data,{binary:true,fin:true},cb); // error callback
}
