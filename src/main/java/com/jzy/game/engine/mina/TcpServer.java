package com.jzy.game.engine.mina;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.executor.OrderedThreadPoolExecutor;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jzy.game.engine.mina.code.DefaultProtocolCodecFactory;
import com.jzy.game.engine.mina.code.ProtocolCodecFactoryImpl;
import com.jzy.game.engine.mina.config.MinaServerConfig;

/**
 * TCP服务器
 *
 * @author JiangZhiYong
 * @date 2017-03-30 QQ:359135103
 */
public class TcpServer implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(TcpServer.class);
	private final MinaServerConfig minaServerConfig;
	private final NioSocketAcceptor acceptor;
	private final IoHandler ioHandler;
	private ProtocolCodecFactoryImpl factory;
	private OrderedThreadPoolExecutor threadpool; // 消息处理线程

	protected boolean isRunning = false; // 服务器是否运行

	/**
	 * 
	 * @param minaServerConfig
	 *            配置
	 * @param ioHandler
	 *            消息处理器
	 */
	public TcpServer(MinaServerConfig minaServerConfig, IoHandler ioHandler) {
		this.minaServerConfig = minaServerConfig;
		this.ioHandler = ioHandler;
		acceptor = new NioSocketAcceptor();
	}

	public TcpServer(MinaServerConfig minaServerConfig, IoHandler ioHandler, ProtocolCodecFactoryImpl factory) {
		this(minaServerConfig, ioHandler);
		this.factory = factory;
	}

	/**
	 * 连接会话数
	 * 
	 * @return
	 */
	public int getManagedSessionCount() {
		return acceptor == null ? 0 : acceptor.getManagedSessionCount();
	}

	/**
	 * 广播所有连接的消息
	 * 
	 * @param obj
	 */
	public void broadcastMsg(Object obj) {
		this.acceptor.broadcast(obj);
	}

	@Override
	public void run() {
		synchronized (this) {
			if (!isRunning) {
				isRunning = true;
				DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();
				if (factory == null) {
					factory = new DefaultProtocolCodecFactory();
				}
				factory.getDecoder().setMaxReadSize(minaServerConfig.getMaxReadSize());
				factory.getEncoder().setMaxScheduledWriteMessages(minaServerConfig.getMaxScheduledWriteMessages());
				chain.addLast("codec", new ProtocolCodecFilter(factory));
				threadpool = new OrderedThreadPoolExecutor(minaServerConfig.getOrderedThreadPoolExecutorSize());
				chain.addLast("threadPool", new ExecutorFilter(threadpool));

				acceptor.setReuseAddress(minaServerConfig.isReuseAddress()); // 允许地址重用

				SocketSessionConfig sc = acceptor.getSessionConfig();
				sc.setReuseAddress(minaServerConfig.isReuseAddress());
				sc.setReceiveBufferSize(minaServerConfig.getReceiveBufferSize());
				sc.setSendBufferSize(minaServerConfig.getSendBufferSize());
				sc.setTcpNoDelay(minaServerConfig.isTcpNoDelay());
				sc.setSoLinger(minaServerConfig.getSoLinger());
				sc.setIdleTime(IdleStatus.READER_IDLE, minaServerConfig.getReaderIdleTime());
				sc.setIdleTime(IdleStatus.WRITER_IDLE, minaServerConfig.getWriterIdleTime());

				acceptor.setHandler(ioHandler);

				try {
					acceptor.bind(new InetSocketAddress(minaServerConfig.getPort()));
					log.warn("已开始监听TCP端口：{}", minaServerConfig.getPort());
				} catch (IOException e) {
					log.warn("监听TCP端口：{}已被占用", minaServerConfig.getPort());
					log.error("TCP 服务异常", e);
				}
			}
		}
	}

	public void stop() {
		synchronized (this) {
			if (!isRunning) {
				log.info("Server " + minaServerConfig.getName() + "is already stoped.");
				return;
			}
			isRunning = false;
			try {
				if (threadpool != null) {
					threadpool.shutdown();
				}
				acceptor.unbind();
				acceptor.dispose();
				log.info("Server is stoped.");
			} catch (Exception ex) {
				log.error("", ex);
			}
		}
	}
}
