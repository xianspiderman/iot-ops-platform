package io.github.xianspiderman.iotops.product;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper mapper;

    public List<Product> list() {
        return mapper.selectList(Wrappers.<Product>lambdaQuery().orderByAsc(Product::getProductCode));
    }

    public Product create(ProductCommand command) {
        if (mapper.exists(Wrappers.<Product>lambdaQuery().eq(Product::getProductCode, command.productCode()))) {
            throw new BusinessException("PRODUCT_CODE_EXISTS", "Product code already exists");
        }
        Product product = new Product();
        product.setProductCode(command.productCode().trim());
        product.setProductName(command.productName().trim());
        product.setModel(command.model());
        product.setCommunicationType(command.communicationType());
        product.setStatus("ENABLED");
        mapper.insert(product);
        return mapper.selectById(product.getId());
    }

    public record ProductCommand(String productCode, String productName, String model, String communicationType) {
    }
}

