package com.takiku.connector.handler;

import com.google.protobuf.Message;
import com.takiku.connector.domain.ClientConnContext;
import com.takiku.connector.service.ConnectorToClientService;
import com.takiku.connector.service.UserOnlineService;
import com.takiku.connector.service.rest.ConnectorRestService;
import com.takiku.connector.config.SpringUtil;
import domain.ack.ClientAckWindow;
import domain.ack.ServerAckWindow;
import exception.IMException;
import internal.InternalAck;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import parse.AbstractPackParser;
import po.ShakeHands;
import po.UserCertification;
import protobuf.PackProtobuf;
import util.WrapWriter;

import java.util.function.Consumer;

/**
 * 处理客户端的消息
 */
@Component
public class ConnectorClientHandler extends SimpleChannelInboundHandler<Message> {
    private static Logger logger = LoggerFactory.getLogger(ConnectorClientHandler.class);
    private FromClientParser fromClientParser;

    private static ConnectorToClientService connectorToClientService;

    private static UserOnlineService userOnlineService;

    private static ConnectorRestService connectorRestService;




    @Autowired
    private ClientConnContext clientConnContext;

    {
        connectorToClientService = SpringUtil.getBean(ConnectorToClientService.class);
        connectorRestService = SpringUtil.getBean(ConnectorRestService.class);
        userOnlineService = SpringUtil.getBean(UserOnlineService.class);
    }


    public ConnectorClientHandler() {
        fromClientParser = new FromClientParser();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        userOnlineService.userOffline(ctx);
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Message message) throws Exception {
        PackProtobuf.Pack pack = (PackProtobuf.Pack) message;
        switch (pack.getPackType()) {
            case HEART:
                fromClientParser.parse(pack.getHeart(), channelHandlerContext);
                break;
            case MSG:
                fromClientParser.parse(pack.getMsg(), channelHandlerContext);
                break;
            case REPLY:
                fromClientParser.parse(pack.getReply(), channelHandlerContext);
                break;
            case SHAKEHANDS:
                fromClientParser.parse(pack.getShakeHands(), channelHandlerContext);
                break;
            case ACK:
                fromClientParser.parse(pack.getAck(), channelHandlerContext);
                break;
        }
    }

    class FromClientParser extends AbstractPackParser {

        @Override
        public void registerParsers() {

            register(PackProtobuf.ShakeHands.class, ((m, channelHandlerContext) -> {
                if (auth(m)) {
                    logger.info("userOnline");
                    userOnlineService.userOnline(m.getUserId(),m.getToken(), channelHandlerContext);
                    channelHandlerContext.writeAndFlush(InternalAck.createAck(m.getMsgId()));
                }
            }));

            register(PackProtobuf.Msg.class, ((m, channelHandlerContext) -> offerChat(m.getHead().getFromId(),m.getHead().getMsgId(), m.getSerial(),
                    m, channelHandlerContext, ignore -> {
                        connectorToClientService.doChatToClientOrTransferAndFlush(m);
                    }
                    ))
            );
            register(PackProtobuf.Reply.class, ((m, channelHandlerContext) -> offerChat(m.getFromId(),m.getMsgId(), m.getSerial(),
                    m, channelHandlerContext, ignore ->
                            connectorToClientService.doReplyToClientOrTransferAndFlush(m))));
            register(PackProtobuf.Heart.class, ((m, channelHandlerContext) ->
                    AckHeart(channelHandlerContext, m)));

            register(PackProtobuf.Ack.class, ((m, channelHandlerContext) -> {
                     connectorToClientService.doAck(m);
            }));
        }
    }

    private void offerChat(String fromId,String id, Long serial, Message m, ChannelHandlerContext ctx, Consumer<Message> consumer) {
        offer(fromId,id, serial, m, ctx, consumer);
    }

    private void offer(String fromId,String id, Long serial, Message copy, ChannelHandlerContext ctx, Consumer<Message> consumer) {
        connectorToClientService.offerChat(fromId,id, serial,
                ctx, copy, consumer);
    }

    private boolean auth(PackProtobuf.ShakeHands shakeHands) {
        UserCertification userCertificationBaseResponse = connectorRestService.certification(new ShakeHands(shakeHands.getUserId(), shakeHands.getToken()));
        if (userCertificationBaseResponse.isResult()) {
            //TODO 加密
            return true;
        }
        return false;
    }

    private void AckHeart(ChannelHandlerContext ctx, PackProtobuf.Heart heart) {
        if (ctx.channel().isOpen() && ctx.channel().isActive()) {
            WrapWriter.writeAckHeart(ctx, heart);
        }
    }

}
