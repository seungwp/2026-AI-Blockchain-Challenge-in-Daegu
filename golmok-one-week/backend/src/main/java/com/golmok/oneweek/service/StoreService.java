package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.StoreResponse;
import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.exception.NotFoundException;
import com.golmok.oneweek.provider.Providers.StoreSearchProvider;
import com.golmok.oneweek.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreSearchProvider storeSearchProvider;
    private final StoreRepository storeRepository;

    public List<StoreResponse> search(String keyword, String city) {
        return storeSearchProvider.search(keyword, city).stream().map(StoreResponse::from).toList();
    }

    public Store getEntity(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("해당 가게를 찾을 수 없습니다. id=" + storeId));
    }

    public StoreResponse get(Long storeId) {
        return StoreResponse.from(getEntity(storeId));
    }

    /** 검색 결과가 없을 때 주소 직접 입력으로 가게를 만든다. */
    @Transactional
    public StoreResponse createFromAddress(String address, String city) {
        return StoreResponse.from(storeSearchProvider.fromAddress(address, city));
    }
}
