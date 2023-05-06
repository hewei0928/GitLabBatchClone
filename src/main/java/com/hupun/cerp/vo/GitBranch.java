package com.hupun.cerp.vo;

import lombok.Data;

@Data
public class GitBranch {
    String name;
    GitBranchCommit commit;
}
