package com.hupun.cerp.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * @Description: git配置
 * @Author: Webb
 * @Date: 2023/4/27 21:12
 **/
@Data
@Configuration
@ConfigurationProperties("git")
public class GitConfig {


	private String gitlabUrl;

	private String apiVersion;

	private String privateToken;

	private String projectDir;

	private String group;

	private Set<String> ignores;
}
