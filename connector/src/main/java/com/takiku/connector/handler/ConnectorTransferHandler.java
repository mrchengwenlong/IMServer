package com.takiku.connector.handler;

import com.google.protobuf.Message;
import conn.Conn;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IdWorker;

import java.util.ArrayList;
import java.util.List;

public class ConnectorTransferHandler extends SimpleChannelInboundHandler<Message> {
    public static final String CONNECTOR_ID = IdWorker.uuid();
    private static Logger logger = LoggerFactory.getLogger(ConnectorTransferHandler.class);
    private static List<ChannelHandlerContext> ctxList = new ArrayList<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("[ConnectorTransfer] connect to transfer");

        putConnectionId(ctx);
        ctxList.add(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        ctxList.remove(ctx);
        ctx.close();
    }

    public static ChannelHandlerContext getOneOfTransferCtx(long time) {
        if (ctxList.size() == 0) {
            logger.warn("connector is not connected to a transfer!");
        }
        return ctxList.get((int) (time % ctxList.size()));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Message message) throws Exception {

    }

    public static List<ChannelHandlerContext> getCtxList() {
        if (ctxList.size() == 0) {
            logger.warn("connector is not connected to a transfer!");
        }
        return ctxList;
    }

    public void putConnectionId(ChannelHandlerContext ctx) {
        ctx.channel().attr(Conn.NET_ID).set(IdWorker.uuid());
    }
}
