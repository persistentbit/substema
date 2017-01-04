package com.persistentbit.substema;

import com.persistentbit.core.result.Result;
import com.persistentbit.jjson.mapping.JJMapper;
import com.persistentbit.jjson.nodes.JJNode;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Peter Muys
 * @since 30/08/2016
 */
public class JSonRemoteService implements RemoteService{
    static private final Logger log = Logger.getLogger(JSonRemoteService.class.getName());
    private RemoteService   service;
    private JJMapper        mapper;

    public JSonRemoteService(RemoteService service,JJMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    public JSonRemoteService(RemoteService service) {
        this(service,new JJMapper());
    }


    @Override
    public void close(long timeOut, TimeUnit timeUnit) {
        service.close();
    }

    @Override
    public Result<RCallResult> call(RCall call) {
        return Result.function(call).code(l -> {
            JJNode callNode = mapper.write(call);
            //log.info(() -> "CALL: " +JJPrinter.print(true,callNode));
            return service.call(mapper.read(callNode, RCall.class))
                .map(cr -> {
                    JJNode node = mapper.write(cr);
                    //log.info(() -> "RESULT: " +JJPrinter.print(true,node));
                    RCallResult callResult = mapper.read(node, RCallResult.class);
                    return callResult;
                });
        });


    }
}
