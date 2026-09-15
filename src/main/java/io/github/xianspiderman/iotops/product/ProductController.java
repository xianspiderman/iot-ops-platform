package io.github.xianspiderman.iotops.product;

import io.github.xianspiderman.iotops.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @GetMapping
    public ApiResponse<List<Product>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<Product> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(service.create(new ProductService.ProductCommand(
                request.productCode(), request.productName(), request.model(), request.communicationType())));
    }

    public record ProductRequest(@NotBlank @Size(max = 64) String productCode,
                                 @NotBlank @Size(max = 128) String productName,
                                 @Size(max = 128) String model,
                                 @Size(max = 32) String communicationType) {
    }
}

