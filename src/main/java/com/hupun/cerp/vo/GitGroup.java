package com.hupun.cerp.vo;

import lombok.Data;

import java.util.List;

@Data
public class GitGroup {
    private Long id;

    private String name;

    private String path;

    private String description;

    private List<GitGroup> subGroups;

    private List<GitProject> projects;

}
