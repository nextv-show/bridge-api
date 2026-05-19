package com.sanshuiyuan.user.api.dto;

import java.util.List;

public class HomeLayoutDto {
    private List<SectionDto> sections;

    public HomeLayoutDto() {}

    public HomeLayoutDto(List<SectionDto> sections) {
        this.sections = sections;
    }

    public List<SectionDto> getSections() { return sections; }
    public void setSections(List<SectionDto> sections) { this.sections = sections; }
}
