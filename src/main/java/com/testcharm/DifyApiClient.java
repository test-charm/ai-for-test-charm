package com.testcharm;

import feign.RequestInterceptor;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "difyApi", url = "${dify.api-endpoint:http://localhost}",
        configuration = DifyApiClient.FeignConfig.class)
public interface DifyApiClient {

    @GetMapping(value = "/datasets", produces = "application/json", consumes = "application/json")
    DataListResponse listDatasets();

    @GetMapping(value = "/datasets/{datasetId}/documents", produces = "application/json", consumes = "application/json")
    DataListResponse listDocuments(@PathVariable("datasetId") String datasetId,
                                   @RequestParam("limit") int limit,
                                   @RequestParam("keyword") String keyword);

    @PostMapping(value = "/datasets/{datasetId}/documents/{documentId}/update-by-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    void updateDocumentByFile(@PathVariable("datasetId") String datasetId,
                              @PathVariable("documentId") String documentId,
                              @RequestPart("file") feign.form.FormData file);

    @Data
    class DataListResponse {
        private List<DataItem> data;
    }

    @Data
    class DataItem {
        private String id;
        private String name;
    }

    class FeignConfig {
        @Bean
        public RequestInterceptor difyAuthInterceptor(
                @Value("${dify.dataset-api-key:}") String apiKey) {
            return template -> template.header("Authorization", "Bearer " + apiKey);
        }

        @Bean
        public Encoder feignFormEncoder() {
            return new SpringFormEncoder();
        }
    }
}
