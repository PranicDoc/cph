import React from "react";

export function ChipInputField({ field, suggestions = [] }: any) {
    const [input, setInput] = React.useState("");

    const add = (value: string) => {
        const v = value.trim();
        if (!v) return;

        const current = field.state.value || [];

        if (!current.includes(v)) {
            field.handleChange([...current, v]);
        }
        setInput("");
    };

    const remove = (value: string) => {
        field.handleChange(field.state.value.filter((v: string) => v !== value));
    };

    const filtered = suggestions.filter(
        (s: string) =>
            s.toLowerCase().includes(input.toLowerCase()) &&
            !(field.state.value || []).includes(s)
    );

    return (
        <div>
            <div className="border-2 rounded-lg p-2 flex flex-wrap gap-2">
                {(field.state.value || []).map((item: string) => (
                    <span key={item} className="chip">
                        {item}
                        <button onClick={() => remove(item)}>x</button>
                    </span>
                ))}

                <input
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") {
                            e.preventDefault();
                            add(input);
                        }
                    }}
                    className="flex-1 outline-none"
                />
            </div>

            {filtered.length > 0 && (
                <div className="dropdown">
                    {filtered.map((s: string) => (
                        <div key={s} onClick={() => add(s)}>
                            {s}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}