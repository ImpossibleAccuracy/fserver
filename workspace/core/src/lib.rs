uniffi::setup_scaffolding!();

#[derive(uniffi::Enum)]
pub enum MathOperation {
    Plus,
    Minus,
}

#[uniffi::export]
pub fn calc(a: f32, b: f32, operation: MathOperation) -> f32 {
    match operation {
        MathOperation::Plus => a + b,
        MathOperation::Minus => a - b,
    }
}
