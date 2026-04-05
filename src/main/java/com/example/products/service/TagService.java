package com.example.products.service;

import com.example.products.model.TagRequest;
import com.example.products.model.TagResponse;

import java.util.List;

public interface TagService {
    TagResponse create(TagRequest request);
    List<TagResponse> findAll();
    TagResponse findById(Long id);
    TagResponse update(Long id, TagRequest request);
    void delete(Long id);
}
