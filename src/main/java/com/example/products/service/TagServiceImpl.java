package com.example.products.service;

import com.example.products.exception.TagNotFoundException;
import com.example.products.model.Tag;
import com.example.products.model.TagRequest;
import com.example.products.model.TagResponse;
import com.example.products.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagServiceImpl implements TagService {

    private final TagRepository repository;

    public TagServiceImpl(TagRepository repository) {
        this.repository = repository;
    }

    @Override
    public TagResponse create(TagRequest request) {
        Tag tag = Tag.builder().name(request.getName()).build();
        return toResponse(repository.save(tag));
    }

    @Override
    public List<TagResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public TagResponse findById(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new TagNotFoundException(id));
    }

    @Override
    public TagResponse update(Long id, TagRequest request) {
        Tag tag = repository.findById(id).orElseThrow(() -> new TagNotFoundException(id));
        tag.setName(request.getName());
        return toResponse(repository.save(tag));
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new TagNotFoundException(id);
        }
        repository.deleteById(id);
    }

    TagResponse toResponse(Tag tag) {
        return TagResponse.builder().id(tag.getId()).name(tag.getName()).build();
    }
}
