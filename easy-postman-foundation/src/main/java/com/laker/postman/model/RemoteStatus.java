package com.laker.postman.model;

/**
 * 远程状态信息封装
 */
public class RemoteStatus {
    public boolean hasRemote = false;
    public boolean hasUpstream = false;
    public String remoteUrl;
    public String currentBranch;
    public String upstreamBranch;
    public String headCommitId;
}