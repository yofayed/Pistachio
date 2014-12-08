/*
 * Copyright 2014 Yahoo! Inc. Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or
 * agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and
 * limitations under the License. See accompanying LICENSE file.
 */

package com.yahoo.ads.pb.network.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import com.yahoo.ads.pb.network.netty.NettyPistachioProtocol.*;
import com.yahoo.ads.pb.exception.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyPistachioClientHandler extends SimpleChannelInboundHandler<Response> {
	private static Logger logger = LoggerFactory.getLogger(NettyPistachioClientHandler.class);

    private static final Pattern DELIM = Pattern.compile("/");

    // Stateful properties
    private volatile Channel channel;
    private final ArrayList<LinkedBlockingQueue<Response>> answerQueues = new ArrayList<LinkedBlockingQueue<Response>>(20);

    private AtomicInteger nextRequestId = new AtomicInteger();

    private final ThreadLocal<Integer> threadAnswerQueueId =
        new ThreadLocal<Integer>() {
            @Override protected Integer initialValue() {
                LinkedBlockingQueue<Response> q = new LinkedBlockingQueue<Response>();
                Integer id = 0;
                synchronized(answerQueues) {
                    answerQueues.add(q);
                    id = answerQueues.size() - 1;
                }
                logger.debug("created blocking queue for thread id: {}", id);
                return id;
            }
        };



    public NettyPistachioClientHandler() {
        super(false);
    }

	public Response sendRequest(Request.Builder builder) throws ConnectionBrokenException {
		//Request.Builder builder = Request.newBuilder();
        builder.setThreadId(threadAnswerQueueId.get());
        Integer requestId = nextRequestId.incrementAndGet() & 0xffff;
        builder.setRequestId(requestId);



        Request request = builder.build();
        channel.writeAndFlush(request);
        logger.debug("request constructed: {} and sent.", request);


        boolean interrupted = false;
		Response response;

        for (;;) {
            try {
                //TODO: in exception caught of handler set some marker and in here can break earlier
                // this may be tricky because can be multiple threads
                response = answerQueues.get(threadAnswerQueueId.get()).poll(100, java.util.concurrent.TimeUnit.SECONDS);
                if (response == null) {
                    logger.debug("times out");

                    // if other threads detect this and reconnect first, it dosen't matter
                    // as we kept channel obj
                    if (!channel.isActive() ) {
                        logger.debug("time out because connection broken");
                        throw new ConnectionBrokenException("time out because conn broken");
                    }

                    return null;
                } else if (response.getRequestId() != requestId)
                {
                    logger.debug("got response {}, but id not match {}, may be the previous timed out response.", response, requestId);
                    continue;
                }
                logger.debug("got response {}", response);
                break;
            } catch (InterruptedException ignore) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

		return response;

	}


    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        channel = ctx.channel();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Response response) throws Exception {
        logger.debug("got response {} in channelRead0", response);
        answerQueues.get(response.getThreadId()).add(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //TODO: add metrics for this
        logger.info("Exception caught", cause);
        ctx.close();
    }
}
