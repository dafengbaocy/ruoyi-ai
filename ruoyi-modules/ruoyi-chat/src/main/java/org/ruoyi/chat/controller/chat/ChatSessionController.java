package org.ruoyi.chat.controller.chat;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.ruoyi.common.excel.utils.ExcelUtil;
import org.ruoyi.common.idempotent.annotation.RepeatSubmit;
import org.ruoyi.core.page.TableDataInfo;
import org.ruoyi.domain.bo.ChatSessionBo;
import org.ruoyi.domain.vo.ChatSessionVo;
import org.ruoyi.service.IChatSessionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.core.page.PageQuery;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.log.enums.BusinessType;

/**
 * 会话管理
 *
 * @author ageerle
 * @date 2025-05-03
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/session")
public class ChatSessionController extends BaseController {

    private final IChatSessionService chatSessionService;

    /**
     * 查询会话管理列表
     */
    @SaCheckPermission("system:session:list")
    @GetMapping("/list")
    public TableDataInfo<ChatSessionVo> list(ChatSessionBo bo, PageQuery pageQuery) {
        return chatSessionService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出会话管理列表
     */
    @SaCheckPermission("system:session:export")
    @Log(title = "会话管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ChatSessionBo bo, HttpServletResponse response) {
        List<ChatSessionVo> list = chatSessionService.queryList(bo);
        ExcelUtil.exportExcel(list, "会话管理", ChatSessionVo.class, response);
    }

    /**
     * 获取会话管理详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("system:session:query")
    @GetMapping("/{id}")
    public R<ChatSessionVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(chatSessionService.queryById(id));
    }

    /**
     * 新增会话管理
     */
    @SaCheckPermission("system:session:add")
    @Log(title = "会话管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ChatSessionBo bo) {
        return toAjax(chatSessionService.insertByBo(bo));
    }

    /**
     * 修改会话管理
     */
    @SaCheckPermission("system:session:edit")
    @Log(title = "会话管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ChatSessionBo bo) {
        return toAjax(chatSessionService.updateByBo(bo));
    }

    /**
     * 删除会话管理
     *
     * @param ids 主键串
     */
    @SaCheckPermission("system:session:remove")
    @Log(title = "会话管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(chatSessionService.deleteWithValidByIds(List.of(ids), true));
    }
}
