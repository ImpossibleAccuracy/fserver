mod calc;

use std::sync::Arc;

uniffi::setup_scaffolding!();

#[derive(uniffi::Enum)]
pub enum MathOperation {
    Plus,
    Minus,
    Divide,
    Multiply,
}

#[uniffi::export(with_foreign)]
pub trait MathModifier: Send + Sync {
    fn modify_result(&self, num: f32) -> f32;
}

#[uniffi::export]
pub fn calc(
    a: f32,
    b: f32,
    operation: MathOperation,
    modifier: Option<Arc<dyn MathModifier>>,
) -> f32 {
    let actual_res = match operation {
        MathOperation::Plus => calc::plus(a, b),
        MathOperation::Minus => calc::minus(a, b),
        MathOperation::Divide => a / b,
        MathOperation::Multiply => a * b,
    };

    match modifier {
        Some(m) => m.modify_result(actual_res),
        None => actual_res,
    }
}
