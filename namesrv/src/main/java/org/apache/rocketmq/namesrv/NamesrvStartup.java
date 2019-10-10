/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.namesrv;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.srvutil.ServerUtil;
import org.apache.rocketmq.srvutil.ShutdownHookThread;
import org.slf4j.LoggerFactory;

public class NamesrvStartup {
    private static InternalLogger log;
    private static Properties properties = null;
    private static CommandLine commandLine = null;

    /**nameserver启动入口**/
    public static void main(String[] args) {
        main0(args);
    }

    public static NamesrvController main0(String[] args) {
        try {
            /**1、解析命令行参数**/
            NamesrvController controller = createNamesrvController(args);
            /**2、初始化controller**/
            start(controller);
            String tip = "The Name Server boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();
            log.info(tip);
            System.out.printf("%s%n", tip);
            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }
        return null;
    }

    /**
     * 生成nameserver的控制器NamesrvController
     * @param args
     * @return
     * @throws IOException
     * @throws JoranException
     */
    public static NamesrvController createNamesrvController(String[] args) throws IOException, JoranException {
        //设置版本号属性:rocketmq.remoting.version
        //TODO 该属性的作用是：把版本号枚举类中的某一个版本对象对应的序号值保存在系统环境变量中，供其他地方调用获取版本号枚举类等
        System.setProperty(RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
        //PackageConflictDetect.detectFastjson();

        //初始化命令行，-h,-n 都为非必填,包含其他例如：-c -p命令则都会抛出：UnrecognizedOptionException: Unrecognized option，退出虚拟机
        Options options = ServerUtil.buildCommandlineOptions(new Options());
        commandLine = ServerUtil.parseCmdLine("mqnamesrv", args, buildCommandlineOptions(options), new PosixParser());
        if (null == commandLine) {
            System.exit(-1);
            return null;
        }
        //初始化nameserver配置
        final NamesrvConfig namesrvConfig = new NamesrvConfig();
        /**设置rocketmq配置文件目录地址，以保证本地启动**/
        namesrvConfig.setRocketmqHome("D:\\github\\person\\rocketmq-read\\distribution");

        final NettyServerConfig nettyServerConfig = new NettyServerConfig();
        nettyServerConfig.setListenPort(9876);

        if (commandLine.hasOption('c')) {
            /**命令行中指定的配置文件配置了namesrvConfig和nettyServerConfig等配置**/
            String file = commandLine.getOptionValue('c');
            if (file != null) {
                InputStream in = new BufferedInputStream(new FileInputStream(file));
                properties = new Properties();
                properties.load(in);
                MixAll.properties2Object(properties, namesrvConfig);
                MixAll.properties2Object(properties, nettyServerConfig);
                namesrvConfig.setConfigStorePath(file);
                System.out.printf("load config properties file OK, %s%n", file);
                in.close();
            }
        }

        /**若有-p命令，则打印所有配置信息，正常退出**/
        if (commandLine.hasOption('p')) {
            InternalLogger console = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_CONSOLE_NAME);
            MixAll.printObjectProperties(console, namesrvConfig);
            MixAll.printObjectProperties(console, nettyServerConfig);
            System.exit(0);
        }

        /**把命令行中的数据设置到Properties中，然后把Properties中的属性设置到namesrvConfig对象中**/
        MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), namesrvConfig);
        if (null == namesrvConfig.getRocketmqHome()) {
            System.out.printf("Please set the %s variable in your environment to match the location of the RocketMQ installation%n", MixAll.ROCKETMQ_HOME_ENV);
            System.exit(-2);
        }

        /**读取日志配置文件，初始化logging system**/
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        configurator.doConfigure(namesrvConfig.getRocketmqHome() + "/conf/logback_namesrv.xml");
        log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);
        MixAll.printObjectProperties(log, namesrvConfig);
        MixAll.printObjectProperties(log, nettyServerConfig);
        log.info("########nameserver日志初始化成功！");

        /**初始化nameserver控制器**/
        final NamesrvController controller = new NamesrvController(namesrvConfig, nettyServerConfig);
        // remember all configs to prevent discard
        //记住所有的配置，防止被丢弃掉，properties是通过命令行指定配置文件，然后读取配置文件中的数据生成的
        controller.getConfiguration().registerConfig(properties);
        return controller;
    }

    public static NamesrvController start(final NamesrvController controller) throws Exception {
        if (null == controller) {
            throw new IllegalArgumentException("NamesrvController is null");
        }
        boolean initResult = controller.initialize();
        if (!initResult) {
            controller.shutdown();
            System.exit(-3);
        }
        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(log, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                controller.shutdown();
                return null;
            }
        }));
        controller.start();
        return controller;
    }

    public static void shutdown(final NamesrvController controller) {
        controller.shutdown();
    }

    /**
     * 添加对-c,-p命令行
     * @param options
     * @return
     */
    public static Options buildCommandlineOptions(final Options options) {
        /**nameserver config配置信息，值是一个路径，对应某个配置文件**/
        //第三个参数hasArg是否需要额外参数，如果是true，则可以在命令行之后添加参数，
        // 例如：-cD:\github\person\rocketmq-read\distribution\conf\broker.conf
        //如果是false，则不能在后面添加参数，例如：-phahahah,
        // 则会抛出异常：org.apache.commons.cli.UnrecognizedOptionException: Unrecognized option: --phahahah
        Option opt = new Option("c", "configFile", true, "Name server config properties file");
        opt.setRequired(false);
        options.addOption(opt);
        /**打印配置信息**/
        opt = new Option("p", "printConfigItem", false, "Print all config item");
        opt.setRequired(false);
        options.addOption(opt);
        return options;
    }

    public static Properties getProperties() {
        return properties;
    }
}
