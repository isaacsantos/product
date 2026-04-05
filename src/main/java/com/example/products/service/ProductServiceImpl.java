package com.example.products.service;

import com.example.products.exception.ProductNotFoundException;
import com.example.products.exception.TagNotFoundException;
import com.example.products.model.*;
import com.example.products.repository.ProductRepository;
import com.example.products.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;
    private final TagRepository tagRepository;

    public ProductServiceImpl(ProductRepository repository, TagRepository tagRepository) {
        this.repository = repository;
        this.tagRepository = tagRepository;
    }

    @Override
    public ProductResponse create(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .build();
        return toResponse(repository.save(product));
    }

    @Override
    public PageResponse<ProductResponse> findAll(int page, int size, Set<Long> tagIds) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Product> result = (tagIds == null || tagIds.isEmpty())
                ? repository.findAll(pageable)
                : repository.findByTagIds(tagIds, pageable);
        List<ProductResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.<ProductResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Override
    public ProductResponse findById(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        return toResponse(repository.save(product));
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public ProductResponse setTags(Long productId, Set<Long> tagIds) {
        Product product = repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        Set<Tag> tags = new HashSet<>();
        for (Long tagId : tagIds) {
            Tag tag = tagRepository.findById(tagId)
                    .orElseThrow(() -> new TagNotFoundException(tagId));
            tags.add(tag);
        }

        product.setTags(tags);
        return toResponse(repository.save(product));
    }

    private ProductResponse toResponse(Product product) {
        List<ImageResponse> images = product.getImages().stream()
                .map(img -> ImageResponse.builder()
                        .id(img.getId())
                        .productId(img.getProductId())
                        .url(img.getUrl())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList();

        List<TagResponse> tags = product.getTags().stream()
                .map(tag -> TagResponse.builder().id(tag.getId()).name(tag.getName()).build())
                .toList();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .images(images)
                .tags(tags)
                .build();
    }
}
