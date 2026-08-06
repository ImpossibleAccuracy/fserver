use network_core::{MathOperation, calc};
use std::io;

fn main() {
    println!("Hi! This is calculator CLI tool.");

    let num1 = read_number("Enter first num:");
    let num2 = read_number("Enter second num:");
    let operator = read_string("Enter operaion:");
    let operator = operator.trim();

    let typed_operator = match operator {
        "+" => MathOperation::Plus,
        "-" => MathOperation::Minus,
        _ => panic!("Invalid operation!"),
    };

    let operation_result = calc(num1, num2, typed_operator, None);

    println!("{} {} {} = {}", num1, operator, num2, operation_result)
}

fn read_string(label: &str) -> String {
    println!("{}", label);

    let mut input = String::new();
    io::stdin()
        .read_line(&mut input)
        .expect("Failed to read line");

    return input;
}

fn read_number(label: &str) -> f32 {
    read_string(label).trim().parse().expect("Invalid number!")
}
