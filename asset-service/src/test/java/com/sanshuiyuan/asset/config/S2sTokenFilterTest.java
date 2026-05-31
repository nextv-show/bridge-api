package com.sanshuiyuan.asset.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入站 S2S 鉴权回归：/internal/ 路径必须带正确 X-S2S-Token，否则 401 短路；
 * 非 /internal/ 路径一律放行（交由 JWT 过滤器）。
 */
class S2sTokenFilterTest {

    private static final String TOKEN = "expected-s2s-token";

    private final S2sTokenFilter filter = new S2sTokenFilter(TOKEN);

    /** 返回 chain 是否被调用（即请求是否放行）。 */
    private boolean run(String uri, String headerToken) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        if (headerToken != null) {
            req.addHeader("X-S2S-Token", headerToken);
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, resp, chain);
        return chain.getRequest() != null;
    }

    @Test
    void internalPath_noToken_rejected401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/internal/x");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, resp, chain);
        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull(); // 短路：未放行
    }

    @Test
    void internalPath_wrongToken_rejected401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/internal/x");
        req.addHeader("X-S2S-Token", "wrong");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, resp, chain);
        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void internalPath_correctToken_passesThrough() throws Exception {
        assertThat(run("/internal/x", TOKEN)).isTrue();
    }

    @Test
    void nonInternalPath_noToken_passesThrough() throws Exception {
        assertThat(run("/orders/mine", null)).isTrue();
    }
}
