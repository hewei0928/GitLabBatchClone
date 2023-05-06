package com.hupun.cerp.service;



import com.hupun.cerp.configuration.GitConfig;
import com.hupun.cerp.vo.GitGroup;
import com.hupun.cerp.vo.GitProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通过gitlab Api自动下载gitLab上的所有项目
 */
@Service
public class GitlabProjectCloneService {

    private final Logger log = LoggerFactory.getLogger(GitlabProjectCloneService.class);

    private final GitConfig gitConfig;

    private final RestTemplate restTemplate;

    private static final String PER_PAGE = "1000";


    private static final ParameterizedTypeReference<List<GitProject>> RESPONSE_TYPE_PROJECT = new ParameterizedTypeReference<List<GitProject>>() {
    };

    private static final ParameterizedTypeReference<List<GitGroup>> RESPONSE_TYPE_GROUP = new ParameterizedTypeReference<List<GitGroup>>() {
    };

    public GitlabProjectCloneService(GitConfig gitConfig, RestTemplate restTemplate) {
        this.gitConfig = gitConfig;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    private void start() {
        List<GitGroup> groups = getGroups();

        if (CollectionUtils.isEmpty(groups)) {
            log.info("用户无group");
            return;
        }
        GitGroup gitGroup = groups.stream()
                .filter(g -> g.getName().equals(this.gitConfig.getGroup()))
                .findAny()
                .orElse(null);

        if (gitGroup == null) {
            log.info("对应group[{}]不存在", this.gitConfig.getGroup());
            return;
        }

        /* 递归获取分组下所有项目以及子分组下所有项目 */
        groupAllProject(gitGroup);
        /* clone */
        cloneByGroup(gitGroup, new File(this.gitConfig.getProjectDir()));
    }

    /**
     * 整组项目clone
     * @param group group
     * @param root 根目录
     */
    private void cloneByGroup(GitGroup group, File root) {
        if (!CollectionUtils.isEmpty(group.getProjects())) {
            for (GitProject project : group.getProjects()) {
                clone(project, root);
            }
        }
        if (!CollectionUtils.isEmpty(group.getSubGroups())) {
            for (GitGroup subGroup : group.getSubGroups()) {
                cloneByGroup(subGroup, root);
            }
        }
    }


    /**
     * 获取group下所有项目以及下级分组的所有项目
     * @param group group
     */
    private void groupAllProject(GitGroup group) {
        long groupId = group.getId();
        /* group下项目 */
        group.setProjects(getProjectsByGroup(groupId));
        group.setSubGroups(getSubGroups(groupId));
        for (GitGroup subGroup : group.getSubGroups()) {
            groupAllProject(subGroup);
        }
    }



    /**
     * 获取指定分组下的项目
     *
     * @param groupId 分组Id
     * @return 所有子项目
     */
    private List<GitProject> getProjectsByGroup(Long groupId) {
        String url = url("/groups/{id}/projects?per_page={per_page}");
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("per_page", PER_PAGE);
        uriVariables.put("id", String.valueOf(groupId));

        ResponseEntity<List<GitProject>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity(), RESPONSE_TYPE_PROJECT, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            List<GitProject> projects = responseEntity.getBody();
            if (!CollectionUtils.isEmpty(projects)) {
                return projects.stream()
                        .filter(g -> !this.gitConfig.getIgnores().contains(g.getName()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取分组列表
     *
     * @return 分组
     */
    private List<GitGroup> getGroups() {
        String url = url("/groups?per_page={per_page}");
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("per_page", PER_PAGE);

        ResponseEntity<List<GitGroup>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity(), RESPONSE_TYPE_GROUP, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }

        return Collections.emptyList();
    }

    /**
     * 获取group下的子分组
     * @param groupId 分组Id
     * @return 子分组
     */
    private List<GitGroup> getSubGroups(long groupId) {
        String url = url("/groups/{id}/subgroups?per_page={per_page}");

        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("per_page", PER_PAGE);
        uriVariables.put("id", String.valueOf(groupId));


        ResponseEntity<List<GitGroup>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity(), RESPONSE_TYPE_GROUP, uriVariables);

        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            List<GitGroup> subGroups = responseEntity.getBody();
            if (!CollectionUtils.isEmpty(subGroups)) {
                return subGroups.stream()
                        .filter(g -> !this.gitConfig.getIgnores().contains(g.getName()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }



    private void clone(GitProject gitProject, File execDir) {
        String command = String.format("git clone -b master %s %s", gitProject.getHttpUrlToRepo(), gitProject.getPathWithNamespace());
        System.out.println("start exec command : " + command);
        try {
            Process exec = Runtime.getRuntime().exec(command, null, execDir);
            exec.waitFor();
            String successResult = StreamUtils.copyToString(exec.getInputStream(), StandardCharsets.UTF_8);
            String errorResult = StreamUtils.copyToString(exec.getErrorStream(), StandardCharsets.UTF_8);
            log.info("successResult: " + successResult);
            log.info("errorResult: " + errorResult);
            log.info("================================");
        } catch (Exception e) {
            log.error("clone项目出错， 项目名称：{}", gitProject.getName(), e);
            e.printStackTrace();
        }
    }



    private String url(String path) {
        return this.gitConfig.getGitlabUrl() + this.gitConfig.getApiVersion() + path;
    }


    private HttpEntity<String> httpEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Private-Token", this.gitConfig.getPrivateToken());
        return new HttpEntity<>(headers);
    }



//    /**
//     * 获取所有项目
//     *
//     * @return
//     */
//    private List<GitProject> getAllProjects() {
//        String url = gitlabUrl + "/api/v3/projects?per_page={per_page}&private_token={private_token}";
//        Map<String, String> uriVariables = new HashMap<>();
//        uriVariables.put("per_page", "100");
//        uriVariables.put("private_token", privateToken);
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        HttpEntity entity = new HttpEntity<>(headers);
//        ParameterizedTypeReference<List<GitProject>> responseType = new ParameterizedTypeReference<List<GitProject>>() {
//        };
//        ResponseEntity<List<GitProject>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
//        if (HttpStatus.OK == responseEntity.getStatusCode()) {
//            return responseEntity.getBody();
//        }
//        return null;
//    }
//
//
//    /**
//     * 获取最近修改的分支名称
//     *
//     * @param projectId 项目ID
//     * @return
//     */
//    private String getLastActivityBranchName(Long projectId) {
//        List<GitBranch> branches = getBranches(projectId);
//        if (CollectionUtils.isEmpty(branches)) {
//            return "";
//        }
//        GitBranch gitBranch = getLastActivityBranch(branches);
//        return gitBranch.getName();
//    }
//
//    /**
//     * 获取指定项目的分支列表
//     * https://docs.gitlab.com/ee/api/branches.html#branches-api
//     *
//     * @param projectId 项目ID
//     * @return
//     */
//    private List<GitBranch> getBranches(Long projectId) {
//        String url = gitlabUrl + "/api/v3/projects/{projectId}/repository/branches?private_token={privateToken}";
//        Map<String, Object> uriVariables = new HashMap<>();
//        uriVariables.put("projectId", projectId);
//        uriVariables.put("privateToken", privateToken);
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        HttpEntity entity = new HttpEntity<>(headers);
//        ParameterizedTypeReference<List<GitBranch>> responseType = new ParameterizedTypeReference<List<GitBranch>>() {
//        };
//        ResponseEntity<List<GitBranch>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
//        if (HttpStatus.OK == responseEntity.getStatusCode()) {
//            return responseEntity.getBody();
//        }
//        return null;
//    }
//
//    /**
//     * 获取最近修改的分支
//     *
//     * @param gitBranches 分支列表
//     * @return
//     */
//    private GitBranch getLastActivityBranch(final List<GitBranch> gitBranches) {
//        GitBranch lastActivityBranch = gitBranches.get(0);
//        for (GitBranch gitBranch : gitBranches) {
//            if (gitBranch.getCommit().getCommittedDate().getTime() > lastActivityBranch.getCommit().getCommittedDate().getTime()) {
//                lastActivityBranch = gitBranch;
//            }
//        }
//        return lastActivityBranch;
//    }
}
