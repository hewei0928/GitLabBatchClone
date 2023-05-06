package com.hupun.cerp.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
public class GitBranchCommit {
    String id;
    @JsonProperty(value = "committed_date")
    Date committedDate;
}
