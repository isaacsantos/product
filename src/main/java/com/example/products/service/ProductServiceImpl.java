package com.example.products.service;

import com.example.products.exception.ProductNotFoundException;
import com.example.products.exception.TagNotFoundException;
import com.example.products.model.*;
import com.example.products.repository.ProductRepository;
import com.example.products.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    // -------------------------------------------------------------------------
    // Legacy methods — kept for backward compatibility
    // -------------------------------------------------------------------------

    @Override
    public ProductResponse create(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .active(request.isActive())
                .conditionType(request.getConditionType() != null ? request.getConditionType() : ConditionType.USED)
                .conditionRating(request.getConditionRating() != null ? request.getConditionRating() : 10)
                .build();
        return toResponse(repository.save(product));
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        if (request.getConditionType() != null) {
            product.setConditionType(request.getConditionType());
        }
        if (request.getConditionRating() != null) {
            product.setConditionRating(request.getConditionRating());
        }
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
        product.setTags(resolveTags(tagIds));
        return toResponse(repository.save(product));
    }

    // -------------------------------------------------------------------------
    // Public endpoints — return PublicProductResponse (no active field)
    // -------------------------------------------------------------------------

    @Override
    public PublicProductResponse findById(Long id) {
        return repository.findById(id)
                .filter(Product::isActive)
                .map(this::toPublicResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public PageResponse<PublicProductResponse> findAll(int page, int size, Set<Long> tagIds) {
        return findAll(page, size, tagIds, null);
    }

    @Override
    public PageResponse<PublicProductResponse> findAll(int page, int size, Set<Long> tagIds, String search) {
        return findAll(page, size, tagIds, search, null);
    }

    @Override
    public PageResponse<PublicProductResponse> findAll(int page, int size, Set<Long> tagIds, String search, ConditionType conditionType) {
        PageRequest pageable = PageRequest.of(page, size);
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasCondition = conditionType != null;

        Page<Product> result;
        if (hasTags && hasSearch && hasCondition) {
            result = repository.findActiveByTagIdsAndConditionTypeAndNameContainingIgnoreCase(tagIds, conditionType, search, pageable);
        } else if (hasTags && hasCondition) {
            result = repository.findActiveByTagIdsAndConditionType(tagIds, conditionType, pageable);
        } else if (hasSearch && hasCondition) {
            result = repository.findByActiveTrueAndConditionTypeAndNameContainingIgnoreCase(conditionType, search, pageable);
        } else if (hasCondition) {
            result = repository.findByActiveTrueAndConditionType(conditionType, pageable);
        } else if (hasTags && hasSearch) {
            result = repository.findActiveByTagIdsAndNameContainingIgnoreCase(tagIds, search, pageable);
        } else if (hasTags) {
            result = repository.findActiveByTagIds(tagIds, pageable);
        } else if (hasSearch) {
            result = repository.findByActiveTrueAndNameContainingIgnoreCase(search, pageable);
        } else {
            result = repository.findByActiveTrue(pageable);
        }

        List<PublicProductResponse> content = result.getContent().stream()
                .map(this::toPublicResponse)
                .toList();
        return PageResponse.<PublicProductResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    // -------------------------------------------------------------------------
    // Admin endpoints — return AdminProductResponse (includes active field)
    // -------------------------------------------------------------------------

    @Override
    public AdminProductResponse createAdmin(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .active(request.isActive())
                .conditionType(request.getConditionType() != null ? request.getConditionType() : ConditionType.USED)
                .conditionRating(request.getConditionRating() != null ? request.getConditionRating() : 10)
                .build();
        return toAdminResponse(repository.save(product));
    }

    @Override
    public PageResponse<AdminProductResponse> findAllAdmin(int page, int size, Set<Long> tagIds) {
        return findAllAdmin(page, size, tagIds, null);
    }

    @Override
    public PageResponse<AdminProductResponse> findAllAdmin(int page, int size, Set<Long> tagIds, String search) {
        return findAllAdmin(page, size, tagIds, search, null);
    }

    @Override
    public PageResponse<AdminProductResponse> findAllAdmin(int page, int size, Set<Long> tagIds, String search, ConditionType conditionType) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("active").ascending());
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasCondition = conditionType != null;

        Page<Product> result;
        if (hasTags && hasSearch && hasCondition) {
            result = repository.findByTagIdsAndConditionTypeAndNameContainingIgnoreCase(tagIds, conditionType, search, pageable);
        } else if (hasTags && hasCondition) {
            result = repository.findByTagIdsAndConditionType(tagIds, conditionType, pageable);
        } else if (hasSearch && hasCondition) {
            result = repository.findByConditionTypeAndNameContainingIgnoreCase(conditionType, search, pageable);
        } else if (hasCondition) {
            result = repository.findByConditionType(conditionType, pageable);
        } else if (hasTags && hasSearch) {
            result = repository.findByTagIdsAndNameContainingIgnoreCase(tagIds, search, pageable);
        } else if (hasTags) {
            result = repository.findByTagIds(tagIds, pageable);
        } else if (hasSearch) {
            result = repository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            result = repository.findAll(pageable);
        }

        List<AdminProductResponse> content = result.getContent().stream()
                .map(this::toAdminResponse)
                .toList();
        return PageResponse.<AdminProductResponse>builder()
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
    public AdminProductResponse findByIdAdmin(Long id) {
        return repository.findById(id)
                .map(this::toAdminResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public AdminProductResponse updateAdmin(Long id, ProductRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setActive(request.isActive());
        if (request.getConditionType() != null) {
            product.setConditionType(request.getConditionType());
        }
        if (request.getConditionRating() != null) {
            product.setConditionRating(request.getConditionRating());
        }
        return toAdminResponse(repository.save(product));
    }

    @Override
    @Transactional
    public AdminProductResponse setTagsAdmin(Long productId, Set<Long> tagIds) {
        Product product = repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.setTags(resolveTags(tagIds));
        return toAdminResponse(repository.save(product));
    }

    // -------------------------------------------------------------------------
    // Private mapping helpers
    // -------------------------------------------------------------------------

    private List<ImageResponse> mapImages(Product product) {
        return product.getImages().stream()
                .map(img -> ImageResponse.builder()
                        .id(img.getId())
                        .productId(img.getProductId())
                        .url(img.getUrl())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList();
    }

    private List<TagResponse> mapTags(Product product) {
        return product.getTags().stream()
                .map(tag -> TagResponse.builder().id(tag.getId()).name(tag.getName()).build())
                .toList();
    }

    private AdminProductResponse toAdminResponse(Product product) {
        return AdminProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .conditionType(product.getConditionType())
                .conditionRating(product.getConditionRating())
                .images(mapImages(product))
                .tags(mapTags(product))
                .active(product.isActive())
                .build();
    }

    private PublicProductResponse toPublicResponse(Product product) {
        return PublicProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .conditionType(product.getConditionType())
                .conditionRating(product.getConditionRating())
                .images(mapImages(product))
                .tags(mapTags(product))
                .build();
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .conditionType(product.getConditionType())
                .conditionRating(product.getConditionRating())
                .images(mapImages(product))
                .tags(mapTags(product))
                .build();
    }

    private Set<Tag> resolveTags(Set<Long> tagIds) {
        Set<Tag> tags = new HashSet<>();
        for (Long tagId : tagIds) {
            Tag tag = tagRepository.findById(tagId)
                    .orElseThrow(() -> new TagNotFoundException(tagId));
            tags.add(tag);
        }
        return tags;
    }
}
