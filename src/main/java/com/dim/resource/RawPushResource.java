package com.dim.resource;

import com.dim.ws.RawDataBroadcaster;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@Path("/raw")
public class RawPushResource {

    @Inject
    RawDataBroadcaster broadcaster;

    @POST
    @Path("/push")
    @Consumes(MediaType.TEXT_PLAIN)
    public void push(String body) {
        broadcaster.broadcastText(body); // broadcast incoming text to websocket clients
    }
}
