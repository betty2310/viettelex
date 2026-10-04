package com.viettelex.android.ime

/**
 * Luật đổi plane (THUẦN — pinned by PlanePolicyTest). Bản Android của iOS PlanePolicy.
 */
object PlanePolicy {
    /**
     * Về plane chữ (ABC từ 123/#+=, emoji, mẫu câu, bảng sửa): IME đánh giá lại viết hoa
     * đầu câu theo context LÚC ĐÓ thay vì để shift tắt (KeyboardView.setPlane hạ shift
     * một-lần). Bug tester iOS 1.2.x, Android cùng bệnh: gõ "." ở plane 123 + "Tự thêm dấu
     * cách" ⇒ ". " rồi về ABC, chữ kế không viết hoa. Stock đánh giá lại.
     * Plane tìm emoji không tính (phím vào ô tìm, không tới ô nhập).
     */
    fun reevaluatesShift(to: Plane): Boolean = to == Plane.LETTERS
}
